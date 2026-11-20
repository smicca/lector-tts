package io.github.smicca.lector

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import io.github.smicca.lector.audio.WavWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

private const val TAG = "LectorBench"

enum class ModelType {
    PIPER_VITS,
    KOKORO,
    KITTEN,
    SUPERTONIC,
    POCKET,
    MARMALADE
}

@Serializable
data class BenchmarkEntry(
    val modelId: String,
    val modelName: String,
    val voice: String,
    val modelType: String,
    val diskSizeBytes: Long,
    val threads: Int,
    val loadTimeMs: Long,
    val ttfaMs: Long,
    val totalSynthesisTimeMs: Long,
    val totalAudioDurationSec: Double,
    val rtf: Double,
    val peakPssMb: Double,
    val sampleRate: Int,
    val batteryTempStartC: Float,
    val batteryTempEndC: Float,
    val thermalStatus: Int,
    val sampleAudioFile: String?,
    val success: Boolean,
    val errorMessage: String? = null
)

@Serializable
data class SustainedRunSnapshot(
    val minute: Int,
    val sentencesSynthesized: Int,
    val intervalSynthMs: Long,
    val intervalAudioSec: Double,
    val intervalRtf: Double,
    val cumulativeRtf: Double,
    val batteryTempC: Float,
    val thermalStatus: Int
)

@Serializable
data class SustainedRunResult(
    val modelId: String,
    val modelName: String,
    val voice: String,
    val threads: Int,
    val totalMinutes: Int,
    val totalSentences: Int,
    val startTempC: Float,
    val endTempC: Float,
    val startRtf: Double,
    val endRtf: Double,
    val rtfDriftPercent: Double,
    val snapshots: List<SustainedRunSnapshot>
)

@Serializable
data class FullBenchmarkReport(
    val deviceModel: String,
    val androidVersion: String,
    val sdkInt: Int,
    val timestamp: String,
    val matrixResults: List<BenchmarkEntry>,
    val sustainedRuns: List<SustainedRunResult>,
    val notes: List<String>
)

class TtsBenchmarkRunner(
    private val context: Context,
    private val modelsBaseDir: File,
    private val outputDir: File
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun getBatteryTemp(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10.0f
    }

    fun getThermalStatus(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.currentThermalStatus ?: -1
        } else -1
    }

    fun getPeakPssMb(): Double {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pids = intArrayOf(android.os.Process.myPid())
            val memInfo = am.getProcessMemoryInfo(pids)
            if (memInfo.isNotEmpty()) memInfo[0].totalPss / 1024.0 else 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    suspend fun runModelBenchmark(
        modelId: String,
        modelName: String,
        voice: String,
        type: ModelType,
        relativeDir: String,
        threads: Int,
        sid: Int = 0,
        sentences: List<String>
    ): BenchmarkEntry = withContext(Dispatchers.IO) {
        val modelDir = File(modelsBaseDir, relativeDir)
        val diskSize = getFolderSize(modelDir)
        val tempStart = getBatteryTemp()

        Log.i(TAG, "Starting bench for $modelId (threads=$threads, dir=$relativeDir)")

        if (type == ModelType.MARMALADE) {
            return@withContext runMarmaladeBenchmark(
                modelId = modelId,
                modelName = modelName,
                voice = voice,
                diskSize = diskSize,
                sentences = sentences,
                tempStart = tempStart
            )
        }

        var tts: OfflineTts? = null
        try {
            val loadStart = SystemClock.elapsedRealtime()
            val config = buildOfflineTtsConfig(modelDir, type, threads, sid)
            tts = OfflineTts(assetManager = null, config = config)
            val loadTimeMs = SystemClock.elapsedRealtime() - loadStart

            val sampleRate = tts.sampleRate()
            var ttfaMs = 0L
            var totalSynthMs = 0L
            var totalSamples = 0L

            val sampleWavFile = File(outputDir, "${modelId}_t${threads}_sample.wav")
            val audioAccumulator = ArrayList<FloatArray>()

            for ((index, sentence) in sentences.withIndex()) {
                val sStart = SystemClock.elapsedRealtime()
                val audio = if (type == ModelType.POCKET) {
                    val refFile = File(modelDir, "test_wavs/bria.wav")
                    val waveData = if (refFile.exists()) WaveReader.readWave(refFile.absolutePath) else null
                    val genConfig = GenerationConfig(
                        referenceAudio = waveData?.samples,
                        referenceSampleRate = waveData?.sampleRate ?: 24000,
                        numSteps = 5
                    )
                    tts.generateWithConfig(sentence, genConfig)
                } else {
                    tts.generate(sentence, sid = sid, speed = 1.0f)
                }
                val sElapsed = SystemClock.elapsedRealtime() - sStart

                if (index == 0) {
                    ttfaMs = sElapsed
                }
                totalSynthMs += sElapsed
                totalSamples += audio.samples.size

                audioAccumulator.add(audio.samples)
            }

            if (totalSamples == 0L) {
                throw IllegalStateException("Zero audio samples generated for $modelId")
            }

            // Write full passage as sample WAV
            if (audioAccumulator.isNotEmpty()) {
                val totalSampleCount = audioAccumulator.sumOf { it.size }
                val merged = FloatArray(totalSampleCount)
                var offset = 0
                for (chunk in audioAccumulator) {
                    System.arraycopy(chunk, 0, merged, offset, chunk.size)
                    offset += chunk.size
                }
                WavWriter.writePcm16(sampleWavFile, merged, sampleRate)
            }

            val totalDurationSec = totalSamples.toDouble() / sampleRate
            val rtf = if (totalDurationSec > 0) (totalSynthMs / 1000.0) / totalDurationSec else 0.0
            val peakPss = getPeakPssMb()
            val tempEnd = getBatteryTemp()
            val thermal = getThermalStatus()

            Log.i(TAG, "Completed $modelId t=$threads: load=${loadTimeMs}ms, TTFA=${ttfaMs}ms, RTF=${"%.3f".format(rtf)}, PSS=${"%.1f".format(peakPss)}MB")

            BenchmarkEntry(
                modelId = modelId,
                modelName = modelName,
                voice = voice,
                modelType = type.name,
                diskSizeBytes = diskSize,
                threads = threads,
                loadTimeMs = loadTimeMs,
                ttfaMs = ttfaMs,
                totalSynthesisTimeMs = totalSynthMs,
                totalAudioDurationSec = totalDurationSec,
                rtf = rtf,
                peakPssMb = peakPss,
                sampleRate = sampleRate,
                batteryTempStartC = tempStart,
                batteryTempEndC = tempEnd,
                thermalStatus = thermal,
                sampleAudioFile = sampleWavFile.name,
                success = true
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed benchmark for $modelId (t=$threads): ${t.message}", t)
            val tempEnd = getBatteryTemp()
            BenchmarkEntry(
                modelId = modelId,
                modelName = modelName,
                voice = voice,
                modelType = type.name,
                diskSizeBytes = diskSize,
                threads = threads,
                loadTimeMs = 0L,
                ttfaMs = 0L,
                totalSynthesisTimeMs = 0L,
                totalAudioDurationSec = 0.0,
                rtf = 0.0,
                peakPssMb = getPeakPssMb(),
                sampleRate = 0,
                batteryTempStartC = tempStart,
                batteryTempEndC = tempEnd,
                thermalStatus = getThermalStatus(),
                sampleAudioFile = null,
                success = false,
                errorMessage = "${t.javaClass.simpleName}: ${t.message}"
            )
        } finally {
            try {
                tts?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private suspend fun runMarmaladeBenchmark(
        modelId: String,
        modelName: String,
        voice: String,
        diskSize: Long,
        sentences: List<String>,
        tempStart: Float
    ): BenchmarkEntry = withContext(Dispatchers.Main) {
        val initDeferred = CompletableDeferred<Boolean>()
        val loadStart = SystemClock.elapsedRealtime()
        var tts: TextToSpeech? = null

        try {
            tts = TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    initDeferred.complete(true)
                } else {
                    initDeferred.complete(false)
                }
            }, "app.marmalade.tts")

            val ok = initDeferred.await()
            val loadTimeMs = SystemClock.elapsedRealtime() - loadStart
            if (!ok) {
                return@withContext BenchmarkEntry(
                    modelId = modelId,
                    modelName = modelName,
                    voice = voice,
                    modelType = ModelType.MARMALADE.name,
                    diskSizeBytes = diskSize,
                    threads = 1,
                    loadTimeMs = loadTimeMs,
                    ttfaMs = 0L,
                    totalSynthesisTimeMs = 0L,
                    totalAudioDurationSec = 0.0,
                    rtf = 0.0,
                    peakPssMb = getPeakPssMb(),
                    sampleRate = 22050,
                    batteryTempStartC = tempStart,
                    batteryTempEndC = getBatteryTemp(),
                    thermalStatus = getThermalStatus(),
                    sampleAudioFile = null,
                    success = false,
                    errorMessage = "Failed to initialize Marmalade TTS engine"
                )
            }

            var ttfaMs = 0L
            var totalSynthMs = 0L
            var totalAudioSec = 0.0
            val sampleWavFile = File(outputDir, "${modelId}_sample.wav")
            val pcmAccumulator = java.io.ByteArrayOutputStream()

            for ((idx, sentence) in sentences.withIndex()) {
                val tempWav = File(outputDir, "marmalade_temp_$idx.wav")
                val sStart = SystemClock.elapsedRealtime()
                val uttDeferred = CompletableDeferred<Boolean>()

                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        uttDeferred.complete(true)
                    }
                    override fun onError(utteranceId: String?) {
                        uttDeferred.complete(false)
                    }
                })

                val params = android.os.Bundle()
                val res = tts.synthesizeToFile(sentence, params, tempWav, "utt_$idx")
                if (res != TextToSpeech.SUCCESS) {
                    throw IllegalStateException("Marmalade synthesizeToFile returned $res")
                }

                uttDeferred.await()
                val sElapsed = SystemClock.elapsedRealtime() - sStart

                if (idx == 0) {
                    ttfaMs = sElapsed
                }
                totalSynthMs += sElapsed

                if (tempWav.exists() && tempWav.length() > 44) {
                    val pcmBytes = tempWav.readBytes().copyOfRange(44, tempWav.length().toInt())
                    pcmAccumulator.write(pcmBytes)
                    totalAudioSec += (pcmBytes.size.toDouble() / (22050 * 2))
                    tempWav.delete()
                }
            }

            val allPcm = pcmAccumulator.toByteArray()
            if (allPcm.isNotEmpty()) {
                val floatSamples = FloatArray(allPcm.size / 2)
                val byteBuffer = java.nio.ByteBuffer.wrap(allPcm).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (i in floatSamples.indices) {
                    floatSamples[i] = byteBuffer.short / 32768.0f
                }
                WavWriter.writePcm16(sampleWavFile, floatSamples, 22050)
            }

            val rtf = if (totalAudioSec > 0) (totalSynthMs / 1000.0) / totalAudioSec else 0.0
            val peakPss = getPeakPssMb()
            val tempEnd = getBatteryTemp()

            BenchmarkEntry(
                modelId = modelId,
                modelName = modelName,
                voice = voice,
                modelType = ModelType.MARMALADE.name,
                diskSizeBytes = diskSize,
                threads = 1,
                loadTimeMs = loadTimeMs,
                ttfaMs = ttfaMs,
                totalSynthesisTimeMs = totalSynthMs,
                totalAudioDurationSec = totalAudioSec,
                rtf = rtf,
                peakPssMb = peakPss,
                sampleRate = 22050,
                batteryTempStartC = tempStart,
                batteryTempEndC = tempEnd,
                thermalStatus = getThermalStatus(),
                sampleAudioFile = sampleWavFile.name,
                success = true
            )
        } finally {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun runSustainedBenchmark(
        modelId: String,
        modelName: String,
        voice: String,
        type: ModelType,
        relativeDir: String,
        threads: Int,
        sid: Int = 0,
        durationMinutes: Int = 10,
        sentences: List<String>
    ): SustainedRunResult = withContext(Dispatchers.IO) {
        val modelDir = File(modelsBaseDir, relativeDir)
        Log.i(TAG, "Starting sustained ${durationMinutes}-minute run for $modelId (t=$threads)")

        val startTemp = getBatteryTemp()
        val config = buildOfflineTtsConfig(modelDir, type, threads, sid)
        val tts = OfflineTts(assetManager = null, config = config)

        val snapshots = mutableListOf<SustainedRunSnapshot>()
        val runStart = SystemClock.elapsedRealtime()
        val totalMs = durationMinutes * 60 * 1000L

        var currentSentenceIdx = 0
        var totalSentences = 0
        var cumulativeSynthMs = 0L
        var cumulativeAudioSec = 0.0

        var minuteStartMs = runStart
        var intervalSynthMs = 0L
        var intervalAudioSec = 0.0
        var intervalSentences = 0
        var currentMinute = 1

        val sampleRate = tts.sampleRate()
        val pocketGenConfig = if (type == ModelType.POCKET) {
            val refFile = File(modelDir, "test_wavs/bria.wav")
            val waveData = if (refFile.exists()) WaveReader.readWave(refFile.absolutePath) else null
            GenerationConfig(
                referenceAudio = waveData?.samples,
                referenceSampleRate = waveData?.sampleRate ?: 24000,
                numSteps = 5
            )
        } else null

        try {
            while (SystemClock.elapsedRealtime() - runStart < totalMs) {
                val sentence = sentences[currentSentenceIdx]
                currentSentenceIdx = (currentSentenceIdx + 1) % sentences.size

                val sStart = SystemClock.elapsedRealtime()
                val audio = if (type == ModelType.POCKET && pocketGenConfig != null) {
                    tts.generateWithConfig(sentence, pocketGenConfig)
                } else {
                    tts.generate(sentence, sid = sid, speed = 1.0f)
                }
                val sElapsed = SystemClock.elapsedRealtime() - sStart

                val audioSec = audio.samples.size.toDouble() / sampleRate
                intervalSynthMs += sElapsed
                intervalAudioSec += audioSec
                intervalSentences++

                cumulativeSynthMs += sElapsed
                cumulativeAudioSec += audioSec
                totalSentences++

                val now = SystemClock.elapsedRealtime()
                if (now - minuteStartMs >= 60_000L || now - runStart >= totalMs) {
                    val intRtf = if (intervalAudioSec > 0) (intervalSynthMs / 1000.0) / intervalAudioSec else 0.0
                    val cumRtf = if (cumulativeAudioSec > 0) (cumulativeSynthMs / 1000.0) / cumulativeAudioSec else 0.0
                    val temp = getBatteryTemp()
                    val thermal = getThermalStatus()

                    val snapshot = SustainedRunSnapshot(
                        minute = currentMinute,
                        sentencesSynthesized = intervalSentences,
                        intervalSynthMs = intervalSynthMs,
                        intervalAudioSec = intervalAudioSec,
                        intervalRtf = intRtf,
                        cumulativeRtf = cumRtf,
                        batteryTempC = temp,
                        thermalStatus = thermal
                    )
                    snapshots.add(snapshot)
                    Log.i(TAG, "Sustained $modelId min $currentMinute: intRTF=${"%.3f".format(intRtf)}, cumRTF=${"%.3f".format(cumRtf)}, temp=${temp}°C")

                    currentMinute++
                    minuteStartMs = now
                    intervalSynthMs = 0L
                    intervalAudioSec = 0.0
                    intervalSentences = 0
                }
            }
        } finally {
            try {
                tts.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        val endTemp = getBatteryTemp()
        val startRtf = snapshots.firstOrNull()?.intervalRtf ?: 0.0
        val endRtf = snapshots.lastOrNull()?.intervalRtf ?: 0.0
        val driftPercent = if (startRtf > 0) ((endRtf - startRtf) / startRtf) * 100.0 else 0.0

        SustainedRunResult(
            modelId = modelId,
            modelName = modelName,
            voice = voice,
            threads = threads,
            totalMinutes = durationMinutes,
            totalSentences = totalSentences,
            startTempC = startTemp,
            endTempC = endTemp,
            startRtf = startRtf,
            endRtf = endRtf,
            rtfDriftPercent = driftPercent,
            snapshots = snapshots
        )
    }

    private fun buildOfflineTtsConfig(
        modelDir: File,
        type: ModelType,
        threads: Int,
        sid: Int
    ): OfflineTtsConfig {
        val dir = modelDir.absolutePath
        return when (type) {
            ModelType.PIPER_VITS -> {
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
            ModelType.KOKORO -> {
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
            ModelType.KITTEN -> {
                val multiLexicon = File(File(modelsBaseDir, "kokoro-int8-multi-lang-v1_0"), "lexicon-us-en.txt")
                getOfflineTtsConfig(
                    modelDir = dir,
                    modelName = "model.int8.onnx",
                    voices = "voices.bin",
                    lexicon = multiLexicon.absolutePath,
                    isKitten = true,
                    numThreads = threads
                )
            }
            ModelType.SUPERTONIC -> {
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
            ModelType.POCKET -> {
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
            ModelType.MARMALADE -> throw IllegalArgumentException("Marmalade is not an OfflineTts engine")
        }
    }
}
