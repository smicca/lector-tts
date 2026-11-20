# Lector

**Lector** is a high-performance, offline-first neural text-to-speech (TTS) engine and benchmark suite for Android, focused on natural-sounding speech for long-form reading, theological literature, and study applications.

## Key Features

- **Local & Offline**: Runs entirely on-device without cloud network latency or telemetry.
- **Neural Model Support**: Powered by `sherpa-onnx` runtime with support for:
  - Kokoro TTS (v0.19 and multi-language v1.0/v1.1 int8)
  - Supertonic TTS
  - Pocket TTS
  - Kitten Nano TTS
  - Piper VITS (medium and high fidelity voices)
- **System TTS Integration**: A/B benchmarking against Android system speech engines (including Marmalade TTS).
- **Domain Text Normalization**: Built-in rules for biblical citations, theological terms, and Latin patristic phrases.
- **Hardware-Aware Benchmarks**: Measures model load latency, Time-To-First-Audio (TTFA), Real-Time Factor (RTF), memory PSS footprint, and thermal/drift behavior across thread counts.

## Adding Voices

Lector supports adding neural voices through two methods:

### 1. Built-in Voice Catalog
Open the **Voices** screen in Lector. The catalog provides ready-to-download official neural models hosted on GitHub (`k2-fsa/sherpa-onnx`):
- **Kokoro multi-lang v1.0 int8** (Recommended): Natural US and UK voices including Michael, Heart, George, and Emma.
- **Supertonic int8**: High-speed, 44.1 kHz acoustic decoder running at 8.2× real-time.
- **Pocket TTS int8**: Autoregressive flow-matching neural voice.
- **Piper VITS**: High and medium clarity voices (`ryan-high`, `lessac-medium`).
- **Kitten Nano int8**: Ultra-lightweight (31 MB) speech synthesis.

Tap **Download** next to any model to download, verify SHA-256 integrity, and extract automatically.

### 2. Import from File (Storage Access Framework)
Tap **Import from file** to select a `.tar.bz2` or `.zip` archive or directory from your device storage or cloud drive. Lector auto-detects the model architecture based on the files contained:

- **Kokoro**: Must contain `model.onnx` (or `model.int8.onnx`), `voices.bin`, and `tokens.txt`.
- **Piper VITS**: Must contain `*.onnx`, `tokens.txt`, and optionally `*.onnx.json` / `espeak-ng-data`.
- **Supertonic**: Must contain acoustic generator ONNX models and tokens.
- **Pocket TTS**: Must contain autoregressive flow model weights and tokens.
- **Kitten / Matcha**: Must contain model weights and tokens dictionary.

If required files are missing, Lector displays a descriptive error without crashing.

## Android System Voice (Text-to-Speech Engine)

Lector registers as a system-wide `TextToSpeechService`. To use Lector as your default Android speech engine:
1. Open **Lector**.
2. Tap **Use Lector as system voice** (or navigate to Android Settings > Accessibility > Text-to-speech output).
3. Select **Lector** as your preferred engine.

## Building

Build the APK using the repository Gradle wrapper or the shared runner:

```bash
/Users/myhome/Code/automation/android-release-process/scripts/run-android-gradle.sh \
  -ProjectPath /Users/myhome/Code/mobile/lector-tts \
  -Tasks assembleDebug
```

## Running Benchmarks

Install the benchmark APK to the connected Android device:

```bash
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Push models to the application's external storage directory:

```bash
adb -s <device-serial> push <models-dir> /sdcard/Android/data/io.github.smicca.lector/files/models
```

Launch the benchmark activity:

```bash
adb -s <device-serial> shell am start -n io.github.smicca.lector/.BenchmarkActivity
```

Detailed model benchmarks and analysis are documented in [docs/research/benchmarks.md](docs/research/benchmarks.md).

## License

GPL-3.0-or-later. Bundles `sherpa-onnx` and `espeak-ng`.
