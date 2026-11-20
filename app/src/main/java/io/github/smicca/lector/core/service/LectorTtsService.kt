package io.github.smicca.lector.core.service

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import io.github.smicca.lector.SpeechNormalizer
import io.github.smicca.lector.core.engine.LectorTtsEngine
import io.github.smicca.lector.core.model.VoiceModelCatalog
import io.github.smicca.lector.core.storage.ModelStorage
import io.github.smicca.lector.core.text.TtsTextSplitter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

private const val TAG = "LectorTtsService"

class LectorTtsService : TextToSpeechService() {

    private lateinit var engine: LectorTtsEngine
    @Volatile
    private var isCancelled = false

    override fun onCreate() {
        super.onCreate()
        engine = LectorTtsEngine.getInstance(this)
        Log.i(TAG, "LectorTtsService initialized")
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val l = lang?.lowercase() ?: return TextToSpeech.LANG_NOT_SUPPORTED
        return if (l == "eng" || l == "en") {
            val c = country?.uppercase() ?: ""
            if (c == "USA" || c == "US" || c == "GBR" || c == "GB") {
                TextToSpeech.LANG_COUNTRY_AVAILABLE
            } else {
                TextToSpeech.LANG_AVAILABLE
            }
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return onIsLanguageAvailable(lang, country, variant)
    }

    override fun onGetLanguage(): Array<String> {
        val prefs = getSharedPreferences("lector_prefs", Context.MODE_PRIVATE)
        val selectedVoice = prefs.getString("selected_voice_id", "en-us-michael") ?: "en-us-michael"
        return if (selectedVoice.startsWith("en-gb")) {
            arrayOf("eng", "GBR", "")
        } else {
            arrayOf("eng", "USA", "")
        }
    }

    override fun onGetVoices(): List<Voice> {
        val catalog = VoiceModelCatalog.loadCatalog(this)
        val installed = ModelStorage.getInstalledModels(this, null)
        val voiceList = mutableListOf<Voice>()

        for (model in installed) {
            val catalogEntry = catalog.firstOrNull { it.id == model.id }
            val voices = catalogEntry?.voices ?: model.voices
            for (v in voices) {
                val locale = if (v.locale.contains("GB")) Locale.UK else Locale.US
                val features = setOf(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)
                voiceList.add(
                    Voice(
                        v.id,
                        locale,
                        Voice.QUALITY_VERY_HIGH,
                        Voice.LATENCY_NORMAL,
                        false,
                        features
                    )
                )
            }
        }

        // If no model is downloaded yet, provide dummy voice metadata so Android TTS settings lists the engine
        if (voiceList.isEmpty()) {
            voiceList.add(
                Voice(
                    "en-us-michael",
                    Locale.US,
                    Voice.QUALITY_VERY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    setOf(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)
                )
            )
        }

        return voiceList
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        isCancelled = false
        val text = request.charSequenceText?.toString() ?: ""
        if (text.isBlank()) {
            callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1)
            callback.done()
            return
        }

        val prefs = getSharedPreferences("lector_prefs", Context.MODE_PRIVATE)
        val selectedModelId = prefs.getString("selected_model_id", null)
        val selectedVoiceId = request.voiceName ?: prefs.getString("selected_voice_id", "en-us-michael") ?: "en-us-michael"

        val installed = ModelStorage.getInstalledModels(this, selectedModelId)
        val model = installed.firstOrNull { it.id == selectedModelId }
            ?: installed.firstOrNull { it.modelType == "KOKORO" }
            ?: installed.firstOrNull()

        if (model == null) {
            Log.w(TAG, "Synthesis failed: No TTS model installed yet")
            callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
            return
        }

        val loaded = engine.ensureModelLoaded(model, numThreads = 4)
        if (!loaded) {
            Log.e(TAG, "Synthesis failed: could not load model ${model.name}")
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }

        val sampleRate = engine.sampleRate()
        val speakerId = resolveSpeakerId(model.modelType, selectedVoiceId)
        val speedRate = (request.speechRate / 100.0f).coerceIn(0.5f, 2.0f)

        callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
        val maxBufferSize = callback.maxBufferSize

        val normalized = SpeechNormalizer.normalize(text)
        val sentences = SpeechNormalizer.splitSentences(normalized)
        val chunks = sentences.flatMap { TtsTextSplitter.split(it, 140) }.filter { it.isNotBlank() }

        for (chunk in chunks) {
            if (isCancelled) {
                Log.i(TAG, "Synthesis cancelled by onStop")
                return
            }

            try {
                val floatSamples = engine.synthesizeSentence(chunk, speakerId, speedRate)
                val pcmBytes = floatToPcm16(floatSamples)

                var offset = 0
                while (offset < pcmBytes.size && !isCancelled) {
                    val toSend = minOf(maxBufferSize, pcmBytes.size - offset)
                    val res = callback.audioAvailable(pcmBytes, offset, toSend)
                    if (res != TextToSpeech.SUCCESS) {
                        Log.w(TAG, "callback.audioAvailable returned $res, aborting")
                        return
                    }
                    offset += toSend
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error during sentence synthesis: ${t.message}", t)
                callback.error(TextToSpeech.ERROR_SYNTHESIS)
                return
            }
        }

        if (!isCancelled) {
            callback.done()
        }
    }

    override fun onStop() {
        Log.i(TAG, "onStop requested")
        isCancelled = true
        engine.stop()
    }

    private fun resolveSpeakerId(modelType: String, voiceId: String): Int {
        if (modelType == "KOKORO") {
            return when (voiceId) {
                "en-us-michael" -> 16
                "en-us-heart" -> 3
                "en-gb-george" -> 26
                "en-gb-emma" -> 21
                "en-us-nicole" -> 0
                "en-us-adam" -> 15
                else -> 16
            }
        }
        return 0
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
}
