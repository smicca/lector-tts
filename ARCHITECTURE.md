# ARCHITECTURE.md

## Architectural Contract: Lector TTS (`lector-tts`)

### 1. Requirements & Goals

Lector provides a benchmark harness and modular on-device neural Text-to-Speech library for Android applications.

- **Offline-First Execution**: In-process inference using ONNX Runtime via `sherpa-onnx` C/C++ JNI libraries. No remote API calls.
- **Candidate Matrix**:
  - Kokoro int8 en (v0.19)
  - Kokoro multi-lang v1.0 / v1.1 int8 (English voices: `af_heart`, `am_michael`, `bf_emma`, `bm_george`)
  - Supertonic int8
  - Pocket TTS int8
  - Kitten nano int8
  - Piper VITS (`en_US-ryan-high`, `en_US-lessac-medium`)
  - Marmalade TTS (`app.marmalade.tts`) system engine baseline
- **Evaluation Dimensions**:
  - Model initialization/load time (ms)
  - Time-To-First-Audio (TTFA ms) for sentence 1
  - Real-Time Factor (RTF) across 2 and 4 inference threads
  - Memory footprint (PSS via `Debug.getMemoryInfo` and dumpsys)
  - Thermal and RTF stability over a sustained 10-minute generation loop
  - Pronunciation of difficult theological, biblical, and patristic terminology

### 2. Module & Source Structure

```
lector-tts/
├── app/
│   ├── build.gradle.kts           # JNI packaging (arm64-v8a), assets, dependencies
│   └── src/main/
│       ├── AndroidManifest.xml    # App manifest, permissions, BenchmarkActivity
│       └── java/io/github/smicca/lector/
│           ├── BenchmarkActivity.kt # Benchmark execution coordinator & results logger
│           ├── SpeechNormalizer.kt  # Text preprocessing and citation expansion
│           ├── TtsBenchmarkRunner.kt# Core benchmarking harness measuring timing & memory
│           └── audio/
│               └── WavWriter.kt     # PCM to WAV audio writer
├── docs/research/
│   ├── benchmarks.md              # Measured results, hardware specs, and ranked recommendations
│   └── bench-results/             # Benchmark JSON results and compressed audio samples
```

### 3. Inference Engine Integration

- **Sherpa-Onnx Version**: `2.0.2` (native libraries for `arm64-v8a` fetched and packaged during build).
- **Decoupled Model Storage**: Models are not bundled in the APK to keep APK size minimal (~15 MB) and allow modular push/pull via `adb push <model> /sdcard/Android/data/io.github.smicca.lector/files/models/`.
- **Engine Instantiation**: Uses `OfflineTts(assetManager = null, config = config)` (`newFromFile`) pointing to absolute storage paths on the device.

### 4. Text Normalization Pipeline

Input text passes through `SpeechNormalizer` before synthesis:
1. Scriptural citations (`1 Cor 15:3-8` -> `First Corinthians chapter 15, verses 3 to 8`, `Jn 6:53` -> `John chapter 6, verse 53`).
2. Magisterial citations (`CCC 1996` -> `Catechism paragraph 1996`).
3. Abbreviations & titles (`St.`, `cf.`, `e.g.`, `DV`).
4. Sentence segmentation preserving clause boundaries.
