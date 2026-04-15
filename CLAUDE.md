# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FSCast is a DLNA Digital Media Renderer (DMR) receiver application that uses Android's Presentation API to display content on multiple screens within a single Android system. It displays a floating video window on a secondary display (driving screen) while providing playback controls on the primary display (central control screen).

**Application Name**: FSCast (FloatingScreenCasting)

**Current Version**: v0.1.0-beta

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

- **Primary Display (中控屏)**: `ui.ComposeMainActivity` - Modern Jetpack Compose control interface with Material 3 design
- **Secondary Display (驾驶屏)**: `presentation.VideoPresentation` - Floating video window using Media3 ExoPlayer

**Key Design Decision**: The driving display is hardcoded to `displayId = 2`. This is specific to the target hardware and may need adjustment for different devices.

### Package Structure

```
com.example.floatingscreencasting/
├── ui/                          # Primary display UI (Jetpack Compose)
│   ├── ComposeMainActivity.kt   # Main control interface
│   └── composable/              # Compose UI components
│       ├── ModernCastingControlCard.kt
│       ├── PlaybackControlCard.kt
│       ├── ModernSettingsCard.kt
│       └── ModernSlider.kt
├── presentation/                # Secondary display (Presentation)
│   └── VideoPresentation.kt      # Video floating window
├── dlna/                        # DLNA DMR Service
│   ├── DlnaDmrService.kt        # DLNA service manager
│   ├── SsdpServer.kt           # SSDP device discovery
│   ├── DlnaHttpServer.kt       # HTTP control server
│   ├── AvTransportManager.kt    # AVTransport protocol
│   └── RenderingControlManager.kt # RenderingControl protocol
├── cache/                      # Video caching
├── audio/                      # Audio routing
├── events/                     # EventBus events
├── history/                    # Playback history
│   └── PlaybackHistoryManager.kt
└── utils/                      # Utility classes
```

### Technology Stack

- **Kotlin** 2.0.21 with Coroutines
- **Jetpack Compose** 1.7.6 for modern UI
- **Material 3** 1.3.1 for Material Design
- **Media3 ExoPlayer** 1.5.1 for video playback
- **EventBus** 3.3.1 for event communication
- **OkHttp** 4.12.0 for HTTP requests

### UI Framework
- **Primary Display**: Jetpack Compose with Material 3 design system
- **iOS Style Design**: Clean, modern visual aesthetics
- **Real-time Controls**: Sliders for window position, size, and transparency

### Important Configuration

- **Application ID**: `com.example.floatingscreencasting`
- **Minimum SDK**: 30 (Android 11)
- **Target SDK**: 36
- **Compile SDK**: 36
- **Version**: 0.1.0-beta
- **Version Code**: 100
- **Default Display Resolution**: 1920x720
- **Default Floating Window Size**: 960x540

### Important Configuration

- **Minimum SDK**: 30 (Android 11)
- **Target SDK**: 36
- **Default Display Resolution**: 1920x720
- **Default Floating Window Size**: 960x540

### Floating Window Behavior

The floating window on the driving display:
- Uses `FLAG_NOT_FOCUSABLE` to allow touch passthrough (non-interactive)
- Position and size can be adjusted in real-time via Compose sliders
- Default position: (480, 90) - centered horizontally
- Shows a "等待投屏..." loading state before video playback
- Supports touch interaction (unlike traditional non-interactive overlays)

### Display Detection Flow

1. First attempts to get display with `displayId = 2` (driving display)
2. Falls back to `DISPLAY_CATEGORY_PRESENTATION` if display 2 is not available
3. Registers `DisplayListener` to handle display hotplug events

## Key Files

### Main Application Files
- `app/src/main/java/com/example/floatingscreencasting/ui/ComposeMainActivity.kt` - Main Compose control interface
- `app/src/main/java/com/example/floatingscreencasting/presentation/VideoPresentation.kt` - Video presentation window
- `app/src/main/AndroidManifest.xml` - Permissions and activity configuration (requires SYSTEM_ALERT_WINDOW)

### DLNA Service Files
- `app/src/main/java/com/example/floatingscreencasting/dlna/DlnaDmrService.kt` - DLNA service manager
- `app/src/main/java/com/example/floatingscreencasting/dlna/SsdpServer.kt` - SSDP device discovery
- `app/src/main/java/com/example/floatingscreencasting/dlna/DlnaHttpServer.kt` - HTTP control server
- `app/src/main/java/com/example/floatingscreencasting/dlna/AvTransportManager.kt` - AVTransport protocol implementation
- `app/src/main/java/com/example/floatingscreencasting/dlna/RenderingControlManager.kt` - RenderingControl protocol implementation

### Configuration Files
- `gradle/libs.versions.toml` - Dependency version catalog
- `app/build.gradle.kts` - Application build configuration
- `build.gradle.kts` - Project build configuration

### Documentation Files
- `README.md` - Project overview and getting started guide
- `CLAUDE.md` - This file (Claude Code development guide)
- `docs/README.md` - Technical documentation index
- `docs/DLNA_DISCOVERY_MECHANISM.md` - DLNA discovery mechanism details
- `docs/protocol/AUDIO_STREAMING_PROTOCOL.md` - WiFi PCM audio streaming protocol (shared by all three apps)

### Companion App Files
- `companion-android/` - Android phone companion app (FSCast Remote)
- `companion-harmony/` - HarmonyOS phone companion app (FSCast Remote)

## Completed Features

### ✅ Implemented
- [x] DLNA DMR full implementation (SSDP, AvTransport, RenderingControl)
- [x] Android Presentation API multi-screen display
- [x] Jetpack Compose + Material 3 UI migration
- [x] Media3 ExoPlayer video playback
- [x] iOS-style visual design
- [x] Video caching and resume playback
- [x] Real-time floating window controls (position/size/transparency)
- [x] Steering wheel media key support
- [x] DLNA progress synchronization
- [x] Custom application icon (white background with F letter)
- [x] GitHub repository and Release setup

### 🔬 Researched (Not Implemented)
- [ ] Bluetooth audio output (system restriction - requires system-level whitelist)
- [ ] SCO audio channel (blocked by AudioService permission)

### 🚧 In Development
- [ ] WiFi PCM audio streaming to phone companion app (bypasses A2DP restriction)
- [ ] Phone companion app (HarmonyOS version - FSCast Remote)
- [ ] Phone companion app (Android version - FSCast Remote)
- [ ] Multi-device support (multiple phones connected simultaneously)

## Development Notes

### Recent Changes (v0.1.0-beta)
- Migrated from View system to Jetpack Compose
- Removed test activities (BluetoothAudioTestActivity, RootAudioTestActivity, DirectAudioTestActivity)
- Cleaned up project structure
- Organized technical documentation in `docs/` directory
- Created GitHub repository and first beta release

### Known Limitations
- **Bluetooth Audio**: System restriction prevents third-party apps from using A2DP audio output on target hardware. Audio plays from speakers only.
- **Display ID**: Hardcoded to `displayId = 2` for specific hardware. May need adjustment for different devices.
- **Package Name**: Still uses `com.example.floatingscreencasting` (should be changed for production)

### Future Enhancements
- Change package name to production domain
- Add more screen ratio options
- Implement audio output device selection
- Add playlist management
- Support more video formats
- Improve error handling and user feedback
