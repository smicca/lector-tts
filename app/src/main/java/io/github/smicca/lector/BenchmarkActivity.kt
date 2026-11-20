package io.github.smicca.lector

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "LectorBench"

class BenchmarkActivity : Activity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var statusTextView: TextView
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        statusTextView = TextView(this).apply {
            textSize = 13f
            setPadding(32, 32, 32, 32)
            text = "Lector TTS Benchmark Suite\nInitializing...\n"
        }
        val scrollView = ScrollView(this).apply {
            addView(statusTextView)
        }
        setContentView(scrollView)

        val mode = intent.getStringExtra("mode") ?: "all"
        val targetModel = intent.getStringExtra("model")
        val sustainedMinutes = intent.getIntExtra("sustainedMinutes", 10)

        activityScope.launch {
            runBenchmarks(mode, targetModel, sustainedMinutes)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun appendStatus(line: String) {
        Log.i(TAG, line)
        runOnUiThread {
            statusTextView.append("$line\n")
        }
    }

    private suspend fun runBenchmarks(mode: String, targetModel: String?, sustainedMinutes: Int) {
        val candidates = listOf(
            File(filesDir, "models"),
            File(getExternalFilesDir(null), "models"),
            File("/sdcard/Android/data/io.github.smicca.lector/files/models"),
            File("/sdcard/Android/data/io.github.smicca.lector.debug/files/models")
        )
        val modelsDir = candidates.firstOrNull { it.exists() && it.listFiles()?.isNotEmpty() == true }
            ?: candidates.first()
        val outputDir = File(getExternalFilesDir(null), "bench-results")
        outputDir.mkdirs()

        appendStatus("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        appendStatus("Models directory: ${modelsDir.absolutePath}")
        appendStatus("Output directory: ${outputDir.absolutePath}")
        appendStatus("Mode: $mode, targetModel: $targetModel")

        val runner = TtsBenchmarkRunner(this, modelsDir, outputDir)
        val sentences = TestPassage.getNormalizedSentences()
        appendStatus("Test passage prepared: ${sentences.size} sentences, ~565 words.")

        val matrixResults = mutableListOf<BenchmarkEntry>()
        val sustainedResults = mutableListOf<SustainedRunResult>()
        val notes = mutableListOf<String>()

        val existingReportFile = File(outputDir, "benchmark_results.json")
        if (existingReportFile.exists()) {
            try {
                val existing = json.decodeFromString<FullBenchmarkReport>(existingReportFile.readText())
                matrixResults.addAll(existing.matrixResults)
                sustainedResults.addAll(existing.sustainedRuns)
                notes.addAll(existing.notes)
                appendStatus("Loaded ${existing.matrixResults.size} existing matrix entries from report.")
            } catch (e: Exception) {
                Log.w(TAG, "Could not load existing report: ${e.message}")
            }
        }
        File(outputDir, "BENCHMARK_DONE.txt").delete()

        // Model definitions
        data class ModelDef(
            val id: String,
            val name: String,
            val voice: String,
            val type: ModelType,
            val relativeDir: String,
            val sid: Int = 0
        )

        val candidateModels = listOf(
            ModelDef("piper-lessac-medium", "Piper en_US lessac-medium", "lessac", ModelType.PIPER_VITS, "vits-piper-en_US-lessac-medium"),
            ModelDef("piper-ryan-high", "Piper en_US ryan-high", "ryan", ModelType.PIPER_VITS, "vits-piper-en_US-ryan-high"),
            ModelDef("kokoro-en-v0_19", "Kokoro int8 en v0.19", "default", ModelType.KOKORO, "kokoro-int8-en-v0_19", 0),
            ModelDef("kokoro-multi-af_heart", "Kokoro multi-lang v1.0 (af_heart)", "af_heart", ModelType.KOKORO, "kokoro-int8-multi-lang-v1_0", 3),
            ModelDef("kokoro-multi-am_michael", "Kokoro multi-lang v1.0 (am_michael)", "am_michael", ModelType.KOKORO, "kokoro-int8-multi-lang-v1_0", 16),
            ModelDef("kokoro-multi-bf_emma", "Kokoro multi-lang v1.0 (bf_emma)", "bf_emma", ModelType.KOKORO, "kokoro-int8-multi-lang-v1_0", 21),
            ModelDef("kokoro-multi-bm_george", "Kokoro multi-lang v1.0 (bm_george)", "bm_george", ModelType.KOKORO, "kokoro-int8-multi-lang-v1_0", 26),
            ModelDef("kitten-nano-int8", "Kitten nano int8", "default", ModelType.KITTEN, "kitten-nano-en-v0_8-int8", 0),
            ModelDef("supertonic-int8", "Supertonic int8", "default", ModelType.SUPERTONIC, "sherpa-onnx-supertonic-tts-int8-2026-03-06", 0),
            ModelDef("pocket-tts-int8", "Pocket TTS int8", "bria", ModelType.POCKET, "sherpa-onnx-pocket-tts-int8-2026-01-26", 0),
            ModelDef("marmalade-tts", "Marmalade TTS", "system", ModelType.MARMALADE, "", 0)
        )

        val modelsToRun = if (targetModel != null) {
            candidateModels.filter { it.id == targetModel }
        } else {
            candidateModels
        }

        if (mode == "tts_service_test") {
            appendStatus("\n--- TESTING LECTOR SYSTEM TTS ENGINE SERVICE ---")
            val ttsDeferred = CompletableDeferred<Boolean>()
            var ttsClient: android.speech.tts.TextToSpeech? = null
            try {
                ttsClient = android.speech.tts.TextToSpeech(this, { status ->
                    ttsDeferred.complete(status == android.speech.tts.TextToSpeech.SUCCESS)
                }, packageName)

                val ready = ttsDeferred.await()
                appendStatus("TextToSpeech init status: $ready")

                val engines = ttsClient.engines
                val foundLector = engines.any { it.name == packageName }
                appendStatus("Available engines count: ${engines.size}, contains Lector: $foundLector")
                for (eng in engines) {
                    appendStatus(" - ${eng.name} (${eng.label})")
                }

                if (ready) {
                    val uttDeferred = CompletableDeferred<Boolean>()
                    val testWav = File(outputDir, "lector_system_service_test.wav")
                    if (testWav.exists()) testWav.delete()

                    ttsClient.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.i(TAG, "Lector TTS Service onStart: $utteranceId")
                        }
                        override fun onDone(utteranceId: String?) {
                            Log.i(TAG, "Lector TTS Service onDone: $utteranceId")
                            uttDeferred.complete(true)
                        }
                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "Lector TTS Service onError: $utteranceId")
                            uttDeferred.complete(false)
                        }
                    })

                    val testPhrase = "In the beginning was the Word, and the Word was with God, and the Word was God."
                    appendStatus("Synthesizing test phrase through Lector TextToSpeechService...")
                    val res = ttsClient.synthesizeToFile(testPhrase, Bundle(), testWav, "system_test_1")
                    appendStatus("synthesizeToFile returned code: $res")

                    val uttSuccess = withTimeoutOrNull(30_000) { uttDeferred.await() } ?: false
                    appendStatus("Utterance completion: $uttSuccess, WAV size: ${testWav.length()} bytes")
                    if (uttSuccess && testWav.length() > 44) {
                        appendStatus("SUCCESS: Lector TextToSpeechService synthesized verified 16-bit PCM audio!")
                    } else {
                        appendStatus("FAILURE: Synthesis did not produce valid audio")
                    }
                }
            } finally {
                ttsClient?.shutdown()
            }
            File(outputDir, "BENCHMARK_DONE.txt").writeText("DONE\n${Date()}\n")
            return
        }

        if (mode == "all" || mode == "matrix") {
            appendStatus("\n--- STARTING TTS MATRIX BENCHMARKS ---")
            for (modelDef in modelsToRun) {
                val threadCounts = if (modelDef.type == ModelType.MARMALADE) {
                    listOf(1)
                } else if (modelDef.type == ModelType.KOKORO) {
                    listOf(2, 4, 6)
                } else {
                    listOf(2, 4)
                }
                for (threads in threadCounts) {
                    appendStatus("\n[Benchmarking] ${modelDef.name} (threads=$threads)...")
                    val entry = runner.runModelBenchmark(
                        modelId = modelDef.id,
                        modelName = modelDef.name,
                        voice = modelDef.voice,
                        type = modelDef.type,
                        relativeDir = modelDef.relativeDir,
                        threads = threads,
                        sid = modelDef.sid,
                        sentences = sentences
                    )
                    matrixResults.add(entry)
                    if (entry.success) {
                        appendStatus("  => OK: load=${entry.loadTimeMs}ms, TTFA=${entry.ttfaMs}ms, RTF=${"%.3f".format(entry.rtf)}, PSS=${"%.1f".format(entry.peakPssMb)}MB")
                    } else {
                        appendStatus("  => FAILED: ${entry.errorMessage}")
                    }
                }
            }
        }

        if (mode == "all" || mode == "sustained") {
            appendStatus("\n--- STARTING SUSTAINED STABILITY / THERMAL RUNS (${sustainedMinutes}m) ---")
            val sustainedCandidates = if (targetModel != null) {
                candidateModels.filter { it.id == targetModel }
            } else {
                listOf(
                    candidateModels.first { it.id == "piper-ryan-high" },
                    candidateModels.first { it.id == "kokoro-multi-af_heart" }
                )
            }

            for (modelDef in sustainedCandidates) {
                appendStatus("\n[Sustained Run] ${modelDef.name} for ${sustainedMinutes} minutes (threads=4)...")
                val sResult = runner.runSustainedBenchmark(
                    modelId = modelDef.id,
                    modelName = modelDef.name,
                    voice = modelDef.voice,
                    type = modelDef.type,
                    relativeDir = modelDef.relativeDir,
                    threads = 4,
                    sid = modelDef.sid,
                    durationMinutes = sustainedMinutes,
                    sentences = sentences
                )
                sustainedResults.removeAll { it.modelId == modelDef.id }
                sustainedResults.add(sResult)
                appendStatus("  => Completed: sentences=${sResult.totalSentences}, RTF start=${"%.3f".format(sResult.startRtf)} -> end=${"%.3f".format(sResult.endRtf)} (drift ${"%.1f".format(sResult.rtfDriftPercent)}%), Temp ${"%.1f".format(sResult.startTempC)}°C -> ${"%.1f".format(sResult.endTempC)}°C")
            }
        }

        val report = FullBenchmarkReport(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()),
            matrixResults = matrixResults,
            sustainedRuns = sustainedResults,
            notes = notes
        )

        val reportJsonFile = File(outputDir, "benchmark_results.json")
        reportJsonFile.writeText(json.encodeToString(report))
        File(outputDir, "BENCHMARK_DONE.txt").writeText("DONE\n${Date()}\n")

        appendStatus("\n==========================================")
        appendStatus("ALL BENCHMARKS COMPLETED SUCCESSFULLY!")
        appendStatus("Results written to: ${reportJsonFile.absolutePath}")
        appendStatus("==========================================")
    }
}
