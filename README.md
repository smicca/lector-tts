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
