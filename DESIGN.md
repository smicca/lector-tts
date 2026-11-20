# DESIGN.md — Lector Design Specification

## 1. Identity & Purpose

**Lector** is an offline-first neural text-to-speech engine and audio reader designed specifically for calm, long-form listening to sacred Scripture, theological treatises, patristic writings, and contemplative literature.

### Design Tenets
- **Reverent Restraint**: The UI serves as an unobtrusive lectern. Text and audio take center stage; visual chrome recedes into the background.
- **Calm Clarity**: No aggressive animations, noisy badges, or em-dash marketing copy. Clean lines, generous typographic breathing room, and predictable navigation.
- **Family Standard Fidelity**: Strictly adopts the shared app-family navigation standard (pinned identity header, all-caps category headers, distinct trailing cues, >=48dp touch targets, parent-first Back hierarchy).
- **True Dual Theme**: First-class support for both Light and Dark themes, seamlessly following system settings without high-contrast jarring or mid-scroll inversions.

---

## 2. Color System

To honor the contemplative, scholarly nature of the application while avoiding both the generic stock Material purple and the overused "beige + brass + oxblood" luxury trap (per `ui-build/heuristics.md`), Lector uses a **Scholarly Slate & Liturgical Lapis** palette.

Liturgical Lapis (`#2563EB` / `#60A5FA`) echoes the illuminated initials and deep pigments of historic manuscripts, providing dignity and legibility without harsh saturation.

### Light Theme
| Role | Hex | Token Usage |
| :--- | :--- | :--- |
| **Primary (Accent)** | `#1E40AF` | Primary action buttons, active voice chips, slider thumbs |
| **On Primary** | `#FFFFFF` | Text/icons on primary button |
| **Primary Container** | `#EFF6FF` | First-run cards, subtle active callouts |
| **On Primary Container** | `#1E3A8A` | Text on primary containers |
| **Background** | `#F8FAFC` | Screen canvas (Slate 50) |
| **Surface** | `#FFFFFF` | Cards, elevated text field surfaces, dialog sheets |
| **Surface Variant** | `#F1F5F9` | Section groupings, secondary cards (Slate 100) |
| **Outline / Border** | `#CBD5E1` | Card borders, text field outlines (Slate 300) |
| **Text Primary** | `#0F172A` | Primary titles, reading text, button labels (Slate 900) |
| **Text Secondary** | `#475569` | Metadata, summaries, supporting copy (Slate 600) |
| **Text Muted** | `#64748B` | Inactive icons, subtle captions (Slate 500) |
| **Success** | `#16A34A` | Model installed badge, active engine confirmation |
| **Error** | `#DC2626` | Stop button, delete confirmations |

### Dark Theme
| Role | Hex | Token Usage |
| :--- | :--- | :--- |
| **Primary (Accent)** | `#60A5FA` | Active highlights, focus rings, play button, slider thumbs |
| **On Primary** | `#0B0F17` | Text/icons on high-contrast primary elements |
| **Primary Container** | `#1E293B` | First-run cards, active voice card highlights |
| **On Primary Container** | `#93C5FD` | Text on primary containers |
| **Background** | `#0B0F17` | Screen canvas (Obsidian Slate) |
| **Surface** | `#131B2A` | Cards, drawer surface, elevated surfaces |
| **Surface Variant** | `#1E293B` | Group containers, speed control panel |
| **Outline / Border** | `#334155` | 1dp borders, dividers |
| **Text Primary** | `#F8FAFC` | Primary titles, reading text |
| **Text Secondary** | `#94A3B8` | Metadata, summaries |
| **Text Muted** | `#64748B` | Inactive icons, subtle captions |
| **Success** | `#34D399` | In-use badges |
| **Error** | `#EF4444` | Stop playback, delete voice button |

---

## 3. Typography & Spacing Tokens

### Type Scale
- **Root Wordmark**: 22sp, Bold (Lector)
- **Screen & Dialog Titles**: 18sp, SemiBold
- **Section Headers (All-Caps)**: 11sp, Bold, `letterSpacing = 0.5.sp`, uppercase
- **Reading Text Area**: 16sp, Regular, `lineHeight = 24.sp` (comfortable for long reading)
- **Card Titles & Row Titles**: 15–16sp, Medium
- **Summaries & Body Descriptions**: 13sp, Regular, muted
- **Badges & Footnotes**: 11–12sp, Medium

### Spacing Grid
Built strictly on the **8dp spacing grid** (4dp, 8dp, 12dp, 16dp, 24dp, 32dp):
- Screen edge padding: `16.dp`
- Card internal padding: `16.dp`
- Row min height & touch targets: `>= 48.dp`
- Leading icon size: `22.dp` or `24.dp`
- Trailing cue size: `20.dp`
- Gap between list cards: `12.dp`
- Corner radius: `8.dp` for rows/cards, `16.dp` for dialogs and welcome banner

---

## 4. Edge-to-Edge & System Bars

- **No Black Strip**: The activity activates full edge-to-edge rendering via `enableEdgeToEdge()`.
- **System Insets**: Pinned top app bars and modal drawers pad top content against `WindowInsets.statusBars`.
- **Bottom Navigation**: Scrollable containers pad bottom content against `WindowInsets.navigationBars` plus comfortable content spacing.

---

## 5. Screen Hierarchy & Component Specifications

### 1. Listen Screen (Home)
- **Top App Bar (Pinned)**:
  - Start: 48dp hamburger button labeled `"Open menu"`.
  - Center/Start: App title `"Lector"` (rendered once; no duplicate titles).
  - Actions: Quick voice selector chip or direct access.
- **First-Run State (No models installed)**:
  - Clean Welcome Card explaining offline neural synthesis.
  - Prominent primary action button: `"Download Michael voice"` (fully unclipped).
  - Secondary size indicator: `"Kokoro int8 • ~180 MB"`.
- **Reading Canvas**:
  - Comfortable, multi-line reading text area prefilled with sample passage or user text.
  - Clean rounded container with subtle 1dp border.
- **Playback Controls**:
  - **Voice Pill**: Displays active voice (e.g., `"American Male (Michael)"`) with dropdown cue, opening a quick voice switcher.
  - **Speed Slider**: Real-time slider with clear step/display (e.g., `"1.00x"`).
  - **Primary Play/Stop Action**:
    - Idle: Prominent `"Speak"` button with `PlayArrow`.
    - Preparing: `"Preparing voice..."` state with indeterminate indicator.
    - Speaking: Prominent `"Stop"` button with `Stop` icon and restrained error/accent tone.
- **Status & Feedback**: Real-time preparation and error messages displayed inline without layout jumps.

### 2. Voices Screen (Dedicated Screen)
- **Top App Bar (Pinned)**:
  - Start: 48dp Back navigation icon labeled `"Back to Listen"`.
  - Title: `"Voices"`.
  - No trailing back or duplicate close affordances.
- **Storage & Import Header**:
  - Live free device storage read-out (e.g., `"42.5 GB free on device"`).
  - `"Import from file"` action button using Android Storage Access Framework.
- **Installed Voices Section (First)**:
  - Cards for each downloaded model on disk.
  - Clear list of available speakers/voices for the model with `"In use"` check mark or `"Use"` button.
  - Delete button opening a confirmation dialog:
    - Dialog explicitly names the voice and freed storage (e.g., *"Delete Kokoro multi-lang v1.0? This will free ~190 MB."*).
- **Available Voices Section**:
  - Catalog entries with title, one-line description, download and installed sizes, quality/speed badges, and a `"Recommended"` badge for Kokoro.
  - Single primary action:
    - `"Download"` button when uninstalled.
    - Download progress indicator with percentage and a `"Cancel"` button.
    - `"In use"` / `"Use"` selector if already installed.

### 3. Navigation Drawer (Family Standard)
- **Pinned Identity Header**:
  - App wordmark `"Lector"` in bold accent color.
  - Subtitle: Version string (e.g., `"v0.1.0 • Offline Neural TTS"`).
  - End: 48dp `"Close menu"` button (`Icons.Default.Close`).
- **Section Groups**:
  - `LISTEN`:
    - Row: `"Listen"` — Returns to Home / closes drawer.
  - `VOICES`:
    - Row: `"Manage voices"` — Dedicated screen (`ArrowForward` cue; closes drawer then navigates).
    - Row: `"Default voice"` — Picker (`ArrowDropDown` cue; displays current active voice as summary and opens picker).
  - `SYSTEM`:
    - Row: `"System speech engine"` — Opens Android TTS settings (`OpenInNew` cue; summary accurately indicates whether Lector is currently the system default engine).
  - `ABOUT`:
    - Row: `"GitHub repository"` — External link (`OpenInNew` cue).
    - Row: `"License"` — Displays GPL-3.0 details (`Description` icon).
- **Nesting & Back Handler**: Exactly one level. System Back navigates parent-first: dialog/picker -> drawer -> main screen.

---

## 6. Adaptive Launcher Icon

- **Motif**: An open liturgical / scholarly book resting horizontally, with clean audio sound waves radiating upward from the binding/pages, signifying spoken sacred text.
- **Layers**:
  - `ic_launcher_background.xml`: Deep Lapis / Midnight Blue solid background (`#162544`).
  - `ic_launcher_foreground.xml`: Bold, flat, stylized open book in crisp white (`#FFFFFF`) with accent audio arc waves in Celestial Cyan (`#60A5FA`).
  - `ic_launcher_monochrome.xml`: Flat silhouette for Android 13+ Material You themed icons.
- **Density Assets**: `mipmap-anydpi-v26` adaptive icon XMLs + high-res 512×512 PNG under `docs/ic_launcher-playstore.png`.
