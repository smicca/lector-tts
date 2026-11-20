# Lector UI Redesign & House-Style Implementation Report

**Document**: `/Users/myhome/Code/mobile/lector-tts/docs/ui-redesign-report.md`  
**Date**: November 20, 2026  
**Target Device**: Samsung Galaxy S22 (`100.84.96.13:5555` via adb over Wi-Fi)  
**Package**: `io.github.smicca.lector.debug`  
**Git Branch**: `main` (DIRECT mode claim)  
**Status**: Completed and physically verified

---

## 1. Executive Summary

Lector's Android application user interface has been completely redesigned and modernized to adhere to the app-family house style (`app-navigation-ui` and `ui-build`). The previous generic Material 3 template suffered from duplicate app titles, stock purple accents, mid-line clipped button copy, lack of navigation hierarchy, and a stock Android placeholder icon.

The redesign introduces:
1. **Calm, Scholarly Brand Identity**: A bespoke design system (*Scholarly Slate & Liturgical Lapis*) documented in [DESIGN.md](../DESIGN.md), tailored for contemplative theological and long-form literature reading.
2. **App-Family Standard Navigation Drawer**: Pinned identity header, categorized functional groups (`LISTEN`, `VOICES`, `SYSTEM`, `ABOUT`), and an `Advanced settings` sub-menu with parent-first Back navigation.
3. **Dedicated Listen Screen**: Pinned top bar with hamburger menu and single title, comfortable reading canvas with prefilled theological test passage, current voice pill with quick picker sheet, stepped speed slider with presets (0.75x–1.5x), and prominent Speak/Stop action.
4. **Dedicated Voices Screen**: Comprehensive voice management showing device storage, SAF archive import, installed voices first with deletion confirmation naming freed storage, and official model catalog with download progress and cancellation.
5. **Polished First-Run Experience**: An unclipped, structured welcome card with clear primary download actions and file size specifications.
6. **Adaptive Launcher Icon**: Custom vector icon combining an open liturgical codex with acoustic sound waves on deep navy, complete with monochrome layer for Android 13+ Material You themed icons and a 512×512px Play Store asset.

All capabilities were deployed wirelessly and physically verified on the Samsung Galaxy S22. Speech synthesis using the Kokoro `am_michael` neural voice was confirmed operational on hardware.

---

## 2. Design System & Visual Identity

The design system is formalized in [DESIGN.md](../DESIGN.md).

### 2.1 Palette: Scholarly Slate & Liturgical Lapis
The palette replaces the default stock purple with deep, tranquil navy and parchment tones that evoke classical scriptoria and study libraries:

| Role | Light Theme Hex | Dark Theme Hex | Purpose |
| :--- | :--- | :--- | :--- |
| **Primary** | `#162544` (Navy) | `#8EBBFF` (Lapis Tint) | Key interactive elements, active voice pill |
| **On Primary** | `#FFFFFF` | `#0B1E38` | High-contrast text on primary |
| **Primary Container** | `#D4E3FC` | `#1E355B` | Active speed preset chips, card accents |
| **Secondary (Accent)**| `#5C7C89` (Slate) | `#9DB2BF` (Muted Slate) | Secondary actions, badges, icons |
| **Background** | `#F8F9FA` (Off-white) | `#0E131F` (Midnight Slate) | Edge-to-edge window backdrop |
| **Surface** | `#FFFFFF` | `#151C2C` | Reading passage cards, modal surfaces |
| **Surface Variant** | `#EBEFF5` | `#222D42` | Input field fill, dividers, borders |
| **Outline** | `#CCD4E0` | `#3A4B6E` | Crisp, subtle card and input borders |

### 2.2 Typography & Spacing Grid
- **Type Scale**: Android system serif / sans-serif with strict hierarchy (Title Large 22sp, Title Medium 16sp, Body Large 16sp, Body Medium 14sp, Label Small 11sp).
- **Grid Discipline**: Strict 8dp grid (4dp micro, 8dp standard, 16dp component margins, 24dp screen padding).
- **Touch Targets**: All interactive elements (hamburger, voice pill, buttons, preset chips) enforce minimum 48dp tap targets with clear content descriptions.
- **Scroll & Viewport Discipline**: The text input field is constrained between 120dp and 200dp (`minHeight = 120.dp`, `maxHeight = 200.dp`), ensuring that the reading card, voice selector, speed controls, and Speak CTA remain visible above the fold on standard phone displays without mandatory scrolling.

---

## 3. Architecture & Component Redesign

### 3.1 Modal Navigation Drawer (`LectorDrawer.kt`)
Conforms strictly to `app-navigation-ui`:
- **Identity Header**: Pinned header displaying the "Lector" title, version subtitle (`v0.1.0 • Offline Neural TTS`), and a 48dp Close button.
- **Group Hierarchy**:
  - `LISTEN`: Home reading screen (`Listen`).
  - `VOICES`: Voice management (`Manage voices`) and current default voice indicator.
  - `SYSTEM`: System speech engine configuration (`System speech engine`) deep-linking to Android TTS settings with truthful status readout.
  - `ABOUT`: Repository links and open-source license attribution (`GPL-3.0-or-later`).
- **Advanced Sub-Menu**: Drill-down screen containing `Simulate first run` (allowing instant preview of the welcome card without clearing device storage) and `Reset sample passage`.
- **Back Discipline**: Pressing Back while in `Advanced settings` returns to the root drawer; pressing Back while in the root drawer closes the drawer.

### 3.2 Listen Screen (`MainScreen.kt`)
- **Pinned Top Bar**: `TopAppBar` with 48dp hamburger icon at top-start, single "Lector" app title, and quick-action icon at top-end opening the Voices screen.
- **Reading Passage Card**: Enclosed within an outlined surface containing section label (`READING PASSAGE`), `Sample` text reloader, `Clear` button, and scrollable text input.
- **Voice Selector Pill**: Prominently shows current voice (`American Male (Michael)`), flags engine status, and opens a modal bottom sheet voice picker.
- **Stepped Speed Slider**: Smooth slider with 0.05x precision plus instant one-tap preset chips (`0.75x`, `1.00x`, `1.25x`, `1.50x`).
- **First-Run Welcome Card**: Displayed when no voice model is present (or when simulation toggle is active). Features an unclipped primary CTA button with clear download label and size readout on a secondary line.
- **Prominent Primary Action**: Fixed-width, high-contrast `Speak` / `Stop` button with live synthesis state ("Speaking with American Male (Michael)").

### 3.3 Voices Screen (`VoicesScreen.kt`)
- **Top Bar**: Pinned bar with 48dp Back navigation button and "Voices" title.
- **Storage & Actions**: Displays live device free storage readout and primary `Import from file` action (Storage Access Framework).
- **Installed Section**: Displays installed models first, showing model size, active voice count, and active voice selection chip.
- **Deletion Safety**: Tapping Delete triggers a confirmation dialog that explicitly names the voice and freed disk space (`Are you sure you want to delete Kokoro multi-lang v1.0 int8? This will free approximately 153 MB of storage.`).
- **Official Catalog**: Categorized cards for available neural models with description, size, and a "Recommended" badge for Kokoro multi-lang v1.0. Download button features real-time percentage progress and cancellation support.

### 3.4 Adaptive Launcher Icon
Designed to match the user's suite of polished utility apps:
- **Foreground (`ic_launcher_foreground.xml`)**: Clean vector artwork depicting an open liturgical book paired with acoustic sound waves.
- **Background (`ic_launcher_background`)**: Solid Liturgical Lapis (`#162544`).
- **Monochrome (`ic_launcher_monochrome.xml`)**: Single-tint vector asset enabling Android 13+ Material You themed icons.
- **Densities**: Generated across `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi` under `app/src/main/res/mipmap-*/`.
- **Store Asset**: 512×512px PNG generated at `docs/ic_launcher-playstore.png`.

---

## 4. Physical Verification & Device Testing

Deployment was performed exclusively via the shared runner script (`deploy-android-to-phone.sh`) to the Samsung Galaxy S22 (`100.84.96.13:5555`).

### 4.1 Deployment & Integrity Verification
```
Target Package: io.github.smicca.lector.debug
Active Model Directory: files/models/kokoro-int8-multi-lang-v1_0/ (153 MB, 15 files)
Preferences:
  selected_model_id = kokoro-int8-multi-lang-v1_0
  selected_voice_id = en-us-michael
Launcher Package Count: Exactly 1 Lector package installed
```

### 4.2 Speech Synthesis Testing
Synthesis was triggered via physical touch injection on the redesigned `Speak` button:
```
11-20 16:31:58.572  6353  6452 W sherpa-onnx: /home/runner/work/sherpa-onnx/sherpa-onnx/sherpa-onnx/csrc/offline-tts-kokoro-impl.h:TokenizeFromLexiconStr:753
11-20 16:31:58.572  6353  6452 W sherpa-onnx: OOV word skipped: 'typologies'
```
Audio synthesis completed successfully using `sherpa-onnx` Kokoro int8 on hardware. Playback stopped cleanly on pressing `Stop`.

### 4.3 Font Scale & Accessibility
Font scale was adjusted to `1.3` on hardware via `settings put system font_scale 1.3`. Inspection of `docs/screenshots/listen_font_scale_1_3.png` confirmed:
- Zero horizontal or vertical text clipping.
- Reading passage card, chips, and buttons scaled proportionally without overlapping.
- All interactive controls maintained >= 48dp touch targets.
- System font scale was safely restored to `1.0`.

---

## 5. Screenshot Gallery

All verification screenshots were captured directly from the Samsung Galaxy S22 display (1080×2340):

| Filename | Theme | Screen / State | Description |
| :--- | :--- | :--- | :--- |
| `docs/screenshots/listen_idle_light.png` | Light | Listen Screen (Idle) | Off-white parchment background, navy accents, unclipped text, voice pill, speed slider |
| `docs/screenshots/listen_idle_dark.png` | Dark | Listen Screen (Idle) | Deep midnight slate background, crisp lapis pill, balanced contrast |
| `docs/screenshots/listen_playing_light.png` | Light | Listen Screen (Playing) | Live playback state, red outlined "Stop" button, sentence synthesis indicator |
| `docs/screenshots/listen_playing_dark.png` | Dark | Listen Screen (Playing) | Dark mode live synthesis state with Stop control |
| `docs/screenshots/first_run_card_light.png` | Light | First-Run Welcome | Unclipped "Download Michael voice" button with secondary "180 MB" readout |
| `docs/screenshots/first_run_card_dark.png` | Dark | First-Run Welcome | Dark mode first-run card via non-destructive simulation toggle |
| `docs/screenshots/voices_screen_light.png` | Light | Voices Screen | Pinned Back bar, free storage readout, installed Kokoro model, catalog cards |
| `docs/screenshots/voices_screen_dark.png` | Dark | Voices Screen | Dark mode voice management screen |
| `docs/screenshots/drawer_root_light.png` | Light | Navigation Drawer | Pinned identity header, LISTEN, VOICES, SYSTEM, ABOUT, Advanced settings |
| `docs/screenshots/drawer_root_dark.png` | Dark | Navigation Drawer | Dark mode navigation drawer conforming to family standard |
| `docs/screenshots/listen_font_scale_1_3.png` | Dark | Font Scale 1.3 | Stress test confirming zero clipping or visual breakdown at 130% font scale |
| `docs/screenshots/launcher.png` | Dark | S22 App Drawer | Launcher screen showing new adaptive icon and single Lector entry |

---

## 6. Build Verification

Automated unit tests and assembly verified with the shared build runner:
```bash
/Users/myhome/Code/automation/android-release-process/scripts/run-android-gradle.sh \
  -ProjectPath /Users/myhome/Code/mobile/lector-tts \
  -Tasks testDebugUnitTest,assembleDebug
```
**Result**: Build Successful (`BUILD SUCCESSFUL in 8s`). All unit tests passed.
