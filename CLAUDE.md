# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FSCast is a DLNA Digital Media Renderer (DMR) receiver application that uses Android's Presentation API to display content on multiple screens within a single Android system. It displays a floating video window on a secondary display (driving screen) while providing playback controls on the primary display (central control screen).

**Target Hardware**: In-car dual-display systems or any multi-screen Android system where the secondary display has a fixed Display ID (typically 2).

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

### Dual Display Architecture

The app uses Android's **Presentation API** to show content on a secondary display:

- **Primary Display (中控屏)**: `ui.MainActivity` - Control interface with sliders for position/size and playback controls
- **Secondary Display (驾驶屏)**: `presentation.VideoPresentation` - Floating video window using Media3 ExoPlayer

**Key Design Decision**: The driving display is hardcoded to `displayId = 2`. This is specific to the target hardware and may need adjustment for different devices.

### Package Structure

```
com.example.floatingscreencasting/
├── ui/                  # Primary display UI
│   └── MainActivity.kt  # Control interface
└── presentation/        # Secondary display (Presentation)
    └── VideoPresentation.kt  # Video floating window
```

### Technology Stack

- **Kotlin** 2.0.21 with Coroutines
- **Media3 ExoPlayer** 1.5.1 for video playback
- **EventBus** 3.3.1 for event communication (configured but not yet utilized)
- **Material Design 3** components
- **ViewBinding** for view access

### Important Configuration

- **Minimum SDK**: 30 (Android 11)
- **Target SDK**: 36
- **Default Display Resolution**: 1920x720
- **Default Floating Window Size**: 960x540

### Floating Window Behavior

The floating window on the driving display:
- Uses `FLAG_NOT_FOCUSABLE` to allow touch passthrough (non-interactive)
- Position and size can be adjusted in real-time via sliders
- Default position: (480, 90) - centered horizontally
- Shows a "等待投屏..." loading state before video playback

### Display Detection Flow

1. First attempts to get display with `displayId = 2` (driving display)
2. Falls back to `DISPLAY_CATEGORY_PRESENTATION` if display 2 is not available
3. Registers `DisplayListener` to handle display hotplug events

## Key Files

- `app/src/main/java/com/example/floatingscreencasting/ui/MainActivity.kt` - Main control interface
- `app/src/main/java/com/example/floatingscreencasting/presentation/VideoPresentation.kt` - Video presentation window
- `app/src/main/AndroidManifest.xml` - Permissions and activity configuration (requires SYSTEM_ALERT_WINDOW)
- `gradle/libs.versions.toml` - Dependency version catalog
