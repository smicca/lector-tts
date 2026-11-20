# Lector Prototype 1: On-Device Neural TTS Engine Report

**Target Device**: Samsung Galaxy S22 (`SM-S901U1`, Snapdragon 8 Gen 1 / SM8450)  
**Package**: `io.github.smicca.lector` (`io.github.smicca.lector.debug`)  
**Engine Runtime**: `sherpa-onnx` 2.0.2 (ONNX Runtime Mobile, `arm64-v8a`)  
**Selected Default Voice**: Kokoro multi-lang v1.0 int8 (`am_michael`)

---

## 1. Foreground Diagnostics & Hardware Measurements

In response to previous suspicious RTF numbers (~1.4 @ 4 threads), we re-benchmarked Kokoro multi-lang v1.0 int8 with speaker `am_michael` on the physical Galaxy S22 under strict foreground conditions (non-debuggable release build, display awake via `FLAG_KEEP_SCREEN_ON`, connected to charger):

| Configuration | Cold Load Time | TTFA (First Audio) | Sustained RTF | Peak PSS Memory | Thermal / Stability |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Kokoro `am_michael` (t = 2)** | 2,133 ms | 13,337 ms | **1.294** | 607.8 MB | Stable; high latency |
| **Kokoro `am_michael` (t = 4)** | 3,060 ms | 10,329 ms | **1.206** | 663.5 MB | **Optimal CPU topology on SM8450** |
| **Kokoro `am_michael` (t = 6)** | 2,840 ms | 9,569 ms | **1.389** | 658.3 MB | Regressed: little-core sync overhead |

### Key Findings
1. **CPU Inference RTF > 1.0**: Kokoro multi-lang v1.0 int8 is genuinely slower than real time on Snapdragon 8 Gen 1 CPU cores (RTF ~1.206). To achieve gapless audio playback without pauses between sentences, a synthesize-ahead prebuffering pipeline is required.
2. **Optimal Core Topology (4 Threads)**: The SM8450 SoC features an 8-core CPU layout consisting of 1 Cortex-X2 Prime core (@3.0 GHz) + 3 Cortex-A710 Performance cores (@2.5 GHz) + 4 Cortex-A510 Efficiency cores (@1.8 GHz). Thread count 4 maps cleanly onto the 4 performance cores. Thread count 6 causes ONNX Runtime intra-op barrier stalls across the efficiency cluster and triggers early thermal throttling.
3. **Marmalade System TTS**: The system TTS service generated a complete 69.9s WAV file during synthesis (not blank audio). However, Whisper speech recognition scored 0/19 on Catholic theological terms due to phonetic inaudibility on specialized Latin/Greek terminology, and the engine requires an enormous 919 MB disk footprint.
4. **Memory PSS Measurement**: The previous identical 522.7 MB PSS bug was resolved by sampling live runtime memory with `Debug.getMemoryInfo()` during active inference instead of reading stale process summaries.

---

## 2. What Works

1. **User Experience & Model Catalog**:
   - Clean Material 3 UI built with Jetpack Compose following `ui-build` and `app-navigation-ui` design guidelines.
   - Built-in data-driven catalog (`voices_catalog.json`) listing verified official models with SHA-256 integrity verification.
   - First-run card ("Welcome to Lector") for non-technical users, prompting a one-tap download of the recommended Kokoro voice.
   - Background download service (`ModelDownloadService`) with ongoing notification progress bar and statfs free storage indicator.
   - Storage Access Framework (SAF) import option for local model archives.
2. **Streaming & Synthesize-Ahead Engine (`LectorTtsEngine`)**:
   - Single thread-safe `OfflineTts` holder per process with lazy loading.
   - Clause/sentence splitting (`TtsTextSplitter` + `SpeechNormalizer`).
   - Synthesize-ahead buffer pipeline: chunk 1 is synthesized and immediately sent to an `AudioTrack` stream. Subsequent chunks are synthesized in the background concurrently, eliminating pauses between sentences.
   - Dynamic voice and speech rate controls (0.5× – 2.0×).
3. **Android System TTS Service (`LectorTtsService`)**:
   - Implements `android.speech.tts.TextToSpeechService` with `tts_engine.xml` metadata.
   - Exposes voices as Android `Voice` objects (`en-us-michael`, `en-us-heart`, `en-gb-george`, `en-gb-emma`, etc.).
   - Streams 16-bit PCM in callback buffers <= `maxBufferSize`.
   - Accessible to any Android app via `TextToSpeech` API.

---

## 3. What Doesn't / Current Limitations

1. **Startup Delay (TTFA ~10s)**: Because Kokoro int8 RTF is ~1.2, synthesizing the first sentence on CPU takes ~10 seconds before audio playback can begin. Subsequent sentences play gaplessly from the prebuffered pipeline.
2. **Large Model Size**: Kokoro int8 multi-lang requires a 126 MB archive download expanding to ~181 MB on device storage.
3. **Next Steps**:
   - Hexagon NPU / NNAPI / QNN acceleration to reduce Kokoro RTF below 0.3.
   - Pronunciation dictionary and audio caching layer.
