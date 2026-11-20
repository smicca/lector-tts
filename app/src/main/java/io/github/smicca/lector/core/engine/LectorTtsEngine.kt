package io.github.smicca.lector.core.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.WaveReader
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import io.github.smicca.lector.SpeechNormalizer
import io.github.smicca.lector.core.model.InstalledModel
import io.github.smicca.lector.core.storage.ModelStorage
import io.github.smicca.lector.core.text.TtsTextSplitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "LectorEngine"

class LectorTtsEngine private constructor(private val context: Context) {

    private val engineLock = Any()
    private var offlineTts: OfflineTts? = null
    private var activeModelFolder: String? = null
    private var activeModelType: String? = null
    private var currentSpeakerId: Int = 16 // am_michael default
    private var currentSpeed: Float = 1.0f

    private var activePlaybackJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val isSpeaking: Boolean
        get() = activePlaybackJob?.isActive == true

    fun setSpeaker(speakerId: Int) {
        synchronized(engineLock) {
            currentSpeakerId = speakerId
        }
    }

    fun setSpeed(speed: Float) {
        synchronized(engineLock) {
            currentSpeed = speed.coerceIn(0.5f, 2.0f)
        }
    }

    fun sampleRate(): Int {
        synchronized(engineLock) {
            return offlineTts?.sampleRate() ?: 24000
        }
    }

    fun isModelLoaded(): Boolean {
        synchronized(engineLock) {
            return offlineTts != null
        }
    }

    fun ensureModelLoaded(model: InstalledModel, numThreads: Int = 4): Boolean {
        synchronized(engineLock) {
            if (offlineTts != null && activeModelFolder == model.folderName && activeModelType == model.modelType) {
                return true
            }

            try {
                offlineTts?.release()
                offlineTts = null

                val modelsDir = ModelStorage.getModelsDir(context)
                val modelDir = File(modelsDir, model.folderName)
                if (!modelDir.exists()) {
                    Log.e(TAG, "Model directory does not exist: ${modelDir.absolutePath}")
                    return false
                }

                val config = buildOfflineTtsConfig(modelDir, model.modelType, numThreads, modelsDir)
                val tts = OfflineTts(assetManager = null, config = config)
                offlineTts = tts
                activeModelFolder = model.folderName
                activeModelType = model.modelType

                Log.i(TAG, "Loaded OfflineTts engine: ${model.name} (${model.modelType}, sampleRate=${tts.sampleRate()}, threads=$numThreads)")
                return true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load OfflineTts for ${model.name}: ${t.message}", t)
                offlineTts = null
                activeModelFolder = null
                activeModelType = null
                return false
            }
        }
    }

    fun synthesizeSentence(
        text: String,
        speakerId: Int? = null,
        speed: Float? = null
    ): FloatArray {
        synchronized(engineLock) {
            val tts = offlineTts ?: throw IllegalStateException("Speech engine is not initialized or model is missing")
            val sid = speakerId ?: currentSpeakerId
            val spd = speed ?: currentSpeed

            val type = activeModelType ?: "KOKORO"
            if (type == "POCKET") {
                val modelsDir = ModelStorage.getModelsDir(context)
                val modelDir = File(modelsDir, activeModelFolder ?: "")
                val refFile = File(modelDir, "test_wavs/bria.wav")
                val waveData = if (refFile.exists()) WaveReader.readWave(refFile.absolutePath) else null
                val genConfig = GenerationConfig(
                    referenceAudio = waveData?.samples,
                    referenceSampleRate = waveData?.sampleRate ?: 24000,
                    numSteps = 5,
                    speed = spd
                )
                val audio = tts.generateWithConfig(text, genConfig)
                return audio.samples
            } else {
                val audio = tts.generate(text, sid = sid, speed = spd)
                return audio.samples
            }
        }
    }

    fun playText(
        text: String,
        speakerId: Int? = null,
        speed: Float? = null,
        onStart: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        stop()

        activePlaybackJob = scope.launch {
            var audioTrack: AudioTrack? = null
            try {
                val normalized = SpeechNormalizer.normalize(text)
                val rawSentences = SpeechNormalizer.splitSentences(normalized)
                val sentenceChunks = rawSentences.flatMap { sentence ->
                    TtsTextSplitter.split(sentence, 140)
                }.filter { it.isNotBlank() }

                if (sentenceChunks.isEmpty()) {
                    onFinished?.invoke()
                    return@launch
                }

                val rate = sampleRate()
                val minBufferSize = AudioTrack.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize * 4, 32768)

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                synchronized(engineLock) {
                    activeAudioTrack = audioTrack
                }

                val pcmQueue = Channel<ByteArray>(Channel.UNLIMITED)
                var producerFinished = false

                // Background synthesis producer
                val synthJob = launch(Dispatchers.IO) {
                    try {
                        for ((idx, chunk) in sentenceChunks.withIndex()) {
                            if (!isActive) break
                            val startMs = SystemClock.elapsedRealtime()
                            val floatSamples = synthesizeSentence(chunk, speakerId, speed)
                            val synthMs = SystemClock.elapsedRealtime() - startMs
                            val audioSec = floatSamples.size.toDouble() / rate
                            Log.i(TAG, "Chunk $idx synth: ${synthMs}ms for ${"%.2f".format(audioSec)}s audio ('${chunk.take(30)}...')")

                            val pcmBytes = floatToPcm16(floatSamples)
                            pcmQueue.send(pcmBytes)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Synthesis producer stopped: ${e.message}")
                    } finally {
                        producerFinished = true
                        pcmQueue.close()
                    }
                }

                // Consumer / Player
                withContext(Dispatchers.IO) {
                    var startedAudio = false
                    for (pcm in pcmQueue) {
                        if (!isActive) break

                        if (!startedAudio) {
                            audioTrack.play()
                            startedAudio = true
                            withContext(Dispatchers.Main) {
                                onStart?.invoke()
                            }
                        }

                        var offset = 0
                        while (offset < pcm.size && isActive) {
                            val toWrite = minOf(4096, pcm.size - offset)
                            val written = audioTrack.write(pcm, offset, toWrite)
                            if (written > 0) {
                                offset += written
                            } else {
                                break
                            }
                        }
                    }

                    // Let final audio drain
                    if (startedAudio && isActive) {
                        // Wait briefly for buffer to play out
                        val remainingMs = (bufferSize.toDouble() / (rate * 2) * 1000).toLong().coerceIn(100, 1500)
                        Thread.sleep(remainingMs)
                    }
                }

                synthJob.join()

                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            } catch (ce: CancellationException) {
                Log.i(TAG, "Playback cancelled")
            } catch (t: Throwable) {
                Log.e(TAG, "Playback error: ${t.message}", t)
                withContext(Dispatchers.Main) {
                    onError?.invoke(t)
                }
            } finally {
                synchronized(engineLock) {
                    try {
                        audioTrack?.stop()
                        audioTrack?.release()
                    } catch (e: Exception) {
                        // ignore
                    }
                    if (activeAudioTrack === audioTrack) {
                        activeAudioTrack = null
                    }
                }
            }
        }
    }

    fun stop() {
        activePlaybackJob?.cancel()
        activePlaybackJob = null
        synchronized(engineLock) {
            try {
                activeAudioTrack?.pause()
                activeAudioTrack?.flush()
                activeAudioTrack?.stop()
                activeAudioTrack?.release()
            } catch (e: Exception) {
                // ignore
            }
            activeAudioTrack = null
        }
    }

    fun release() {
        stop()
        synchronized(engineLock) {
            try {
                offlineTts?.release()
            } catch (e: Exception) {
                // ignore
            }
            offlineTts = null
            activeModelFolder = null
            activeModelType = null
        }
    }

    private fun floatToPcm16(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val pcm = (clamped * 32767.0f).toInt().toShort()
            buffer.putShort(pcm)
        }
        return bytes
    }

    private fun buildOfflineTtsConfig(
        modelDir: File,
        type: String,
        threads: Int,
        modelsBaseDir: File
    ): OfflineTtsConfig {
        val dir = modelDir.absolutePath
        return when (type) {
            "PIPER_VITS" -> {
                val candidateFiles = listOf(
                    File(modelDir, "en_US-lessac-medium.onnx"),
                    File(modelDir, "en_US-ryan-high.onnx"),
                    File(modelDir, "model.onnx")
                )
                val onnxFile = candidateFiles.firstOrNull { it.exists() }
                    ?: modelDir.listFiles { _, name -> name.endsWith(".onnx") }?.firstOrNull()
                    ?: File(modelDir, "model.onnx")
                val piperLexicon = File(modelsBaseDir, "lexicon-en-us.txt")
                getOfflineTtsConfig(
                    modelDir = dir,
                    modelName = onnxFile.name,
                    lexicon = if (piperLexicon.exists()) piperLexicon.absolutePath else "",
                    numThreads = threads
                )
            }
            "KOKORO" -> {
                val localLexicon = File(modelDir, "lexicon-us-en.txt")
                val multiLexicon = File(File(modelsBaseDir, "kokoro-int8-multi-lang-v1_0"), "lexicon-us-en.txt")
                val lexiconPath = if (localLexicon.exists()) localLexicon.absolutePath else multiLexicon.absolutePath
                getOfflineTtsConfig(
                    modelDir = dir,
                    modelName = "model.int8.onnx",
                    voices = "voices.bin",
                    lexicon = lexiconPath,
                    numThreads = threads
                )
            }
            "KITTEN" -> {
                val multiLexicon = File(File(modelsBaseDir, "kokoro-int8-multi-lang-v1_0"), "lexicon-us-en.txt")
                getOfflineTtsConfig(
                    modelDir = dir,
                    modelName = "model.int8.onnx",
                    voices = "voices.bin",
                    lexicon = if (multiLexicon.exists()) multiLexicon.absolutePath else "",
                    isKitten = true,
                    numThreads = threads
                )
            }
            "SUPERTONIC" -> {
                getOfflineTtsConfig(
                    modelDir = dir,
                    isSupertonic = true,
                    durationPredictor = "duration_predictor.int8.onnx",
                    textEncoder = "text_encoder.int8.onnx",
                    vectorEstimator = "vector_estimator.int8.onnx",
                    supertonicVocoder = "vocoder.int8.onnx",
                    ttsJson = "tts.json",
                    unicodeIndexer = "unicode_indexer.bin",
                    voiceStyle = "voice.bin",
                    numThreads = threads
                )
            }
            "POCKET" -> {
                getOfflineTtsConfig(
                    modelDir = dir,
                    isPocket = true,
                    lmFlow = "lm_flow.int8.onnx",
                    lmMain = "lm_main.int8.onnx",
                    pocketEncoder = "encoder.onnx",
                    pocketDecoder = "decoder.int8.onnx",
                    textConditioner = "text_conditioner.onnx",
                    vocabJson = "vocab.json",
                    tokenScoresJson = "token_scores.json",
                    numThreads = threads
                )
            }
            else -> throw IllegalArgumentException("Unsupported model type: $type")
        }
    }

    companion object {
        @Volatile
        private var instance: LectorTtsEngine? = null

        fun getInstance(context: Context): LectorTtsEngine {
            return instance ?: synchronized(this) {
                instance ?: LectorTtsEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
