# AGENTS.md

Instructions for AI coding agents working on Lector (`lector-tts`).

## Project Overview

Lector is an open-source, offline-first on-device Text-to-Speech (TTS) engine and benchmark suite for Android. It evaluates and deploys high-quality neural speech synthesis models powered by `sherpa-onnx` (ONNX Runtime mobile engine) and Android system TTS engines.

- **Package ID**: `io.github.smicca.lector`
- **Application Name**: Lector
- **License**: GPL-3.0-or-later (due to espeak-ng bundling)
- **Target Platform**: Android (minSdk 26, targetSdk 35/36, primary reference hardware: Snapdragon 8 Gen 1 / SM8450)

## Repository Layout

```
.
├── app/                  # Android application & benchmark harness module
│   ├── build.gradle.kts  # Build configuration, JNI packaging & sherpa-onnx setup
│   └── src/main/         # Kotlin sources, AndroidManifest, resources
├── docs/
│   └── research/         # Model benchmarks, hardware reports, audio samples
├── gradle/               # Wrapper and build-logic configuration
├── build.gradle.kts      # Root build script
├── settings.gradle.kts   # Project and module definitions
└── gradle.properties     # JVM and build flags
```

## Engineering Rules

- **Build Runner**: Always execute builds using the shared Darwin runner:
  `/Users/myhome/Code/automation/android-release-process/scripts/run-android-gradle.sh -ProjectPath /Users/myhome/Code/mobile/lector-tts -Tasks <tasks>`
- **Modest Memory**: Keep Gradle memory usage modest (`org.gradle.jvmargs=-Xmx2g`).
- **No Public Web Access**: Do not make outgoing network requests except documented GitHub release downloads via `gh`.
- **Hardware Targeting**: Primary physical test device is Galaxy S22 (`100.84.96.13:5555`). Do not change system defaults or touch unauthorized devices.
