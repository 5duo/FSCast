# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FSCast is a DLNA Digital Media Renderer (DMR) receiver application that uses Android's Presentation API to display content on multiple screens within a single Android system. It displays a floating video window on a secondary display (driving screen) while providing playback controls on the primary display (central control screen).

**Application Name**: FSCast (FloatingScreenCasting)

**Current Version**: v0.1.0-beta

**Target Hardware**: In-car dual-display systems or any multi-screen Android system where the secondary display has a fixed Display ID (typically 2).

## Build & Run Commands

### 车机端 (Android)

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

### 鸿蒙手机端 (HarmonyOS)

**前提条件**: 鸿蒙开发工具位于 `C:\command-line-tools`

```bash
# 编译鸿蒙应用
cd companion-harmony
cmd.exe //c "C:\\command-line-tools\\hvigor\\bin\\hvigorw.bat assembleHap --mode module -p module=entry@default -p product=default"

# 安装到鸿蒙设备
C:/command-line-tools/sdk/default/openharmony/toolchains/hdc.exe install entry/build/default/outputs/default/entry-default-signed.hap

# 查看设备日志
C:/command-line-tools/sdk/default/openharmony/toolchains/hdc.exe hilog | grep FSCastRemote

# 连接无线调试设备
C:/command-line-tools/sdk/default/openharmony/toolchains/hdc.exe tconn 192.168.x.x:xxxxx
```

**编译产物位置**:
- 未签名 HAP: `entry/build/default/outputs/default/entry-default-unsigned.hap`
- 已签名 HAP: `entry/build/default/outputs/default/entry-default-signed.hap`

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
│   ├── MainActivity.kt           # Legacy View system control interface
│   └── composable/              # Compose UI components
│       ├── ModernCastingControlCard.kt
│       ├── ModernPlaybackCard.kt  # Playback control with audio output selector
│       ├── ModernSettingsCard.kt
│       └── ModernSlider.kt
├── presentation/                # Secondary display (Presentation)
│   ├── VideoPresentation.kt      # Video floating window
│   └── SingleScreenVideoDialog.kt # Single screen video dialog
├── dlna/                        # DLNA DMR Service & DMC Client
│   ├── DlnaDmrService.kt        # DLNA DMR service manager
│   ├── DlnaHttpServer.kt        # HTTP control server (DMR)
│   ├── DlnaDmcClient.kt        # DLNA DMC client (controller)
│   ├── AudioOutputController.kt  # Audio output mode manager
│   ├── PhoneDeviceManager.kt    # Phone device manager
│   ├── SsdpServer.kt           # SSDP device discovery
│   ├── AvTransportManager.kt    # AVTransport protocol
│   └── RenderingControlManager.kt # RenderingControl protocol
├── websocket/                   # WebSocket communication
│   └── CarWebSocketServer.kt    # WebSocket server for phone connection
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
- `app/src/main/java/com/example/floatingscreencasting/ui/MainActivity.kt` - Legacy View system control interface
- `app/src/main/java/com/example/floatingscreencasting/presentation/VideoPresentation.kt` - Video presentation window
- `app/src/main/AndroidManifest.xml` - Permissions and activity configuration (requires SYSTEM_ALERT_WINDOW)

### DLNA Service Files
- `app/src/main/java/com/example/floatingscreencasting/dlna/DlnaDmrService.kt` - DLNA service manager
- `app/src/main/java/com/example/floatingscreencasting/dlna/DlnaHttpServer.kt` - HTTP control server (DMR)
- `app/src/main/java/com/example/floatingscreencasting/dlna/DlnaDmcClient.kt` - DLNA DMC client (controller)
- `app/src/main/java/com/example/floatingscreencasting/dlna/AudioOutputController.kt` - Audio output mode manager
- `app/src/main/java/com/example/floatingscreencasting/dlna/PhoneDeviceManager.kt` - Phone device manager
- `app/src/main/java/com/example/floatingscreencasting/dlna/SsdpServer.kt` - SSDP device discovery
- `app/src/main/java/com/example/floatingscreencasting/dlna/AvTransportManager.kt` - AVTransport protocol implementation
- `app/src/main/java/com/example/floatingscreencasting/dlna/RenderingControlManager.kt` - RenderingControl protocol implementation

### WebSocket Communication Files
- `app/src/main/java/com/example/floatingscreencasting/websocket/CarWebSocketServer.kt` - WebSocket server for phone connection

### UI Components
- `app/src/main/java/com/example/floatingscreencasting/ui/composable/ModernPlaybackCard.kt` - Playback control with audio output selector
- `app/src/main/java/com/example/floatingscreencasting/ui/composable/DevicePairingDialog.kt` - Device pairing dialog

### Configuration Files
- `gradle/libs.versions.toml` - Dependency version catalog
- `app/build.gradle.kts` - Application build configuration
- `build.gradle.kts` - Project build configuration

### Documentation Files
- `README.md` - Project overview and getting started guide
- `CLAUDE.md` - This file (Claude Code development guide)
- `docs/README.md` - Technical documentation index
- `docs/DLNA_DISCOVERY_MECHANISM.md` - DLNA discovery mechanism details

### Companion App Files
- `companion-android/` - Android phone companion app (FSCast Remote)
- `companion-harmony/` - HarmonyOS phone companion app (FSCast Remote)
  - `entry/src/main/ets/pages/Index.ets` - Main page with DLNA DMR
  - `entry/src/main/ets/service/DlnaHttpServer.ets` - DLNA HTTP server
  - `entry/src/main/ets/service/SsdpServer.ets` - SSDP device discovery
  - `entry/src/main/ets/service/VideoPlayer.ets` - Video player with HTTP headers support
  - `entry/src/main/ets/service/CarWebSocketClient.ets` - WebSocket client for car connection

## 投屏功能业务流程

### 1. 基础投屏流程（B站 → 车机）

```
┌─────────┐    DLNA     ┌─────────────┐
│  B站App │ ──────────> │   FSCast    │
│ (手机)  │  (投屏)   │  (车机端)    │
└─────────┘           └─────────────┘
                            │
                            ▼
                       ┌─────────────┐
                       │ 驾驶屏显示   │
                       │   视频画面   │
                       │   中控屏控制 │
                       └─────────────┘
```

**详细步骤**:
1. **设备发现**: B站App通过SSDP发现FSCast设备（伪装成小米电视）
2. **投屏请求**: B站App发送SetAVTransportURI命令（包含视频URL和HTTP头）
3. **反爬虫处理**: FSCast提取HTTP头（user-agent、referer、cookie等）
4. **视频播放**: VideoPresentation使用ExoPlayer播放视频
5. **播放控制**: 通过Play/Pause/Seek命令控制播放

### 2. 音频分离播放流程（B站 → 车机画面 + 手机声音）

```
┌─────────┐    DLNA     ┌─────────────┐    DLNA     ┌──────────────┐
│  B站App │ ──────────> │   FSCast    │ ──────────> │ FSCast Remote │
│ (手机)  │  (投屏)   │  (车机端)    │  (推送)   │  (鸿蒙手机)   │
└─────────┘           └─────────────┘           └──────────────┘
                            │                              │
                            ▼                              ▼
                       ┌─────────────┐              ┌──────────────┐
                       │ 驾驶屏显示   │              │ 手机播放      │
                       │   视频画面   │              │ 视频+音频    │
                       │   (静音)     │              │              │
                       └─────────────┘              └──────────────┘
```

**详细步骤**:
1. **投屏开始**: B站投屏到FSCast，视频开始在车机播放
2. **用户切换**: 用户在车机UI点击"切换到手机"按钮
3. **车机静音**: AudioOutputController设置车机视频静音
4. **推送到手机**: 车机通过DlnaDmcClient将视频URL和HTTP头推送到手机
5. **手机播放**: 手机端接收DLNA命令，使用createMediaSourceWithUrl播放（带HTTP头）
6. **进度同步**: 车机每500ms同步进度到手机，确保两个设备进度一致

### 3. HTTP头传递链路（B站反爬虫支持）

```
B站App → [HTTP Headers] → FSCast DlnaHttpServer
                                    │
                                    ▼
                          提取HTTP头到lastHttpHeaders
                                    │
                                    ▼
                        FSCast VideoPresentation (播放)
                                    │
                    用户切换音频输出时
                                    ▼
                        DlnaDmcClient.setAvTransportURI()
                                    │
                    ┌───────────────────────┴──────────────────────┐
                    │             DLNA元数据 (custom标签)           │
                    │  <custom xmlns=".../headers">              │
                    │    {"user-agent": "...", "referer": "..."}   │
                    └───────────────────────┬──────────────────────┘
                                            ▼
                        FSCast Remote DlnaHttpServer
                                            │
                                            ▼
                                    extractHttpHeadersFromMetadata()
                                            │
                                            ▼
                        VideoPlayer.play(uri, httpHeaders)
                                            │
                                            ▼
                        createMediaSourceWithUrl(uri, httpHeaders)
```

### 4. 设备发现和管理

**车机端发现手机**:
1. DlnaDmcClient发送M-SEARCH广播
2. 手机端SsdpServer响应NOTIFY消息
3. PhoneDeviceManager过滤和存储设备列表
4. UI显示已连接设备数量和名称

**设备过滤规则**:
- 设备名包含"FSCast"、"Remote"、"Phone"
- 制造商包含"HarmonyOS"、"Honor"

### 5. 进度同步机制

**同步触发**:
- 车机端VideoPresentation定期（每10秒）保存播放进度
- AudioOutputController每500ms检查进度并同步到手机

**同步方式**:
- 车机端通过DlnaDmcClient.seek()发送Seek命令到手机
- 手机端VideoPlayer在差异>100ms时执行seek
- 网络延迟补偿：考虑传输时间

### 6. 音频输出模式

**SPEAKER模式** (默认):
- 车机端播放视频+音频
- 手机端停止播放
- 车机视频不静音

**PHONE模式**:
- 车机端播放视频（静音）
- 手机端播放视频+音频
- 进度自动同步

**切换时机**:
- 用户手动切换（点击UI按钮）
- 自动切换（检测到设备连接/断开）

## Completed Features

### ✅ Implemented
- [x] DLNA DMR full implementation (SSDP, AvTransport, RenderingControl)
- [x] DLNA DMC client implementation (control other DLNA devices)
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
- [x] **Audio output separation (car screen video + phone audio)**
- [x] **Bilibili anti-crawler support (HTTP headers transmission)**
- [x] **Phone device discovery and management**
- [x] **WebSocket communication between car and phone**
- [x] **Real-time progress synchronization between devices**
- [x] **Audio output mode switching UI**

### 🔬 Researched (Not Implemented)
- [ ] Bluetooth audio output (system restriction - requires system-level whitelist)
- [ ] SCO audio channel (blocked by AudioService permission)

### 🚧 In Development
- [ ] Multi-device support (multiple phones connected simultaneously)
- [ ] Advanced audio routing (A2DP, USB audio)
- [ ] Playlist management
- [ ] More video format support

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
