# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FSCast is a DLNA Digital Media Renderer (DMR) receiver application that uses Android's Presentation API to display content on multiple screens within a single Android system. It displays a floating video window on a secondary display (driving screen) while providing playback controls on the primary display (central control screen). It also supports audio output separation to a companion phone app via WebSocket communication.

**Application Name**: FSCast (FloatingScreenCasting)

**Current Version**: v0.2.0

**Target Hardware**: In-car dual-display systems or any multi-screen Android system where the secondary display has a fixed Display ID (typically 2).

**Companion App**: FSCast Remote (HarmonyOS) - Receives video from car and plays audio

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

### WebSocket Communication Architecture

The app uses WebSocket for bidirectional communication between car and phone:

```
┌─────────────────┐         WebSocket          ┌─────────────────┐
│   Car (FSCast)  │ ◄──────────────────────► │  Phone (Remote) │
│                 │      (Port 9999)         │                 │
│  Commands:      │                           │  Commands:      │
│  - play_and_seek│                           │  - state_update │
│  - resume       │                           │  - error        │
│  - pause        │                           │                 │
│  - seek         │                           │                 │
│  - progress     │                           │                 │
└─────────────────┘                           └─────────────────┘
```

**Synchronized Startup Flow**:
1. Car pauses playback and sends `play_and_seek` command with current position
2. Phone loads video, seeks to position, and pauses (waiting state)
3. Car waits 2 seconds for phone to load
4. Car sends `resume` command
5. Both devices start playing simultaneously

**Progress Synchronization**:
- Car sends progress update every 10 seconds
- Phone checks difference with car's position
- If difference > 2 seconds, phone seeks to car's position
- Ensures both devices stay synchronized

**Disconnection Protection**:
- Phone detects WebSocket disconnection and immediately pauses playback
- Prevents audio from continuing while video stops
- Car also detects connection state and updates UI accordingly

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
    └── Logger.kt               # Unified logging utility
```

### Technology Stack

- **Kotlin** 2.0.21 with Coroutines
- **Jetpack Compose** 1.7.6 for modern UI
- **Material 3** 1.3.1 for Material Design
- **Media3 ExoPlayer** 1.5.1 for video playback
- **EventBus** 3.3.1 for event communication
- **OkHttp** 4.12.0 for HTTP requests
- **Java-WebSocket** for WebSocket communication

### UI Framework
- **Primary Display**: Jetpack Compose with Material 3 design system
- **iOS Style Design**: Clean, modern visual aesthetics
- **Real-time Controls**: Sliders for window position, size, and transparency

### Important Configuration

- **Application ID**: `com.example.floatingscreencasting`
- **Minimum SDK**: 30 (Android 11)
- **Target SDK**: 36
- **Compile SDK**: 36
- **Version**: 0.2.0
- **Version Code**: 200
- **Default Display Resolution**: 1920x720
- **Default Floating Window Size**: 960x540

### Floating Window Behavior

The floating window on the driving display:
- Supports touch interaction (not FLAG_NOT_FOCUSABLE)
- Position and size can be adjusted in real-time via Compose sliders
- Default position: (480, 90) - centered horizontally
- Shows a "等待投屏..." loading state before video playback
- Automatically mutes when audio output is switched to phone

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
- `companion-harmony/entry/src/main/ets/service/CarWebSocketClient.ets` - WebSocket client for phone

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
┌─────────┐    DLNA     ┌─────────────┐    WebSocket  ┌──────────────┐
│  B站App │ ──────────> │   FSCast    │ ────────────> │ FSCast Remote │
│ (手机)  │  (投屏)   │  (车机端)    │   (推送)     │  (鸿蒙手机)   │
└─────────┘           └─────────────┘               └──────────────┘
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
3. **同步启动**:
   - 车机暂停播放
   - 发送`play_and_seek`命令（包含当前进度）
   - 手机加载视频、跳转到指定进度、暂停等待
   - 等待2秒让手机加载
   - 车机发送`resume`命令
   - 两端同时开始播放
4. **进度同步**: 车机每10秒同步进度到手机
5. **断线保护**: 手机检测到连接断开立即暂停

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
                        WebSocket.sendPlayCommand(uri, headers)
                                    │
                    ┌───────────────┴────────────────┐
                    │     WebSocket JSON Message     │
                    │  {"type":"command",            │
                    │   "action":"play_and_seek",    │
                    │   "data":{                     │
                    │     "uri":"...",               │
                    │     "headers":{...},           │
                    │     "position":...             │
                    │   }}                           │
                    └───────────────┬────────────────┘
                                    ▼
                        FSCast Remote CarWebSocketClient
                                    │
                                    ▼
                        VideoPlayer.play(uri, httpHeaders)
                                    │
                                    ▼
                        createMediaSourceWithUrl(uri, httpHeaders)
```

### 4. WebSocket消息协议

**车机 → 手机（命令）**:

```javascript
// 播放并跳转（同步启动用）
{
  "type": "command",
  "action": "play_and_seek",
  "timestamp": 1234567890,
  "data": {
    "uri": "https://example.com/video.mp4",
    "headers": {
      "user-agent": "...",
      "referer": "..."
    },
    "position": 120000  // 毫秒
  }
}

// 恢复播放（同步启动用）
{
  "type": "command",
  "action": "resume",
  "timestamp": 1234567890,
  "data": {}
}

// 暂停
{
  "type": "command",
  "action": "pause",
  "timestamp": 1234567890,
  "data": {}
}

// 跳转
{
  "type": "command",
  "action": "seek",
  "timestamp": 1234567890,
  "data": {
    "position": 120000
  }
}

// 进度更新（同步用）
{
  "type": "command",
  "action": "progress",
  "timestamp": 1234567890,
  "data": {
    "position": 120000,
    "duration": 3600000,
    "isPlaying": true
  }
}
```

**手机 → 车机（状态更新）**:

```javascript
// 连接成功
{
  "type": "connected",
  "data": {
    "deviceType": "harmonyos_phone",
    "timestamp": 1234567890
  }
}

// 状态更新
{
  "type": "state_update",
  "data": {
    "position": 120000,
    "duration": 3600000,
    "isPlaying": true,
    "timestamp": 1234567890
  }
}

// 错误报告
{
  "type": "error",
  "data": {
    "message": "Failed to load video",
    "timestamp": 1234567890
  }
}
```

### 5. 进度同步机制

**同步触发**:
- 车机端AudioOutputController每10秒检查一次进度
- 发送`progress`命令到手机端
- 手机端收到后检查与车机端的差异

**同步方式**:
- 手机端VideoPlayer检查差异是否>2秒
- 如果是，执行seek操作对齐进度
- 考虑网络延迟，不频繁同步

### 6. 音频输出模式

**SPEAKER模式** (默认):
- 车机端播放视频+音频
- 手机端停止播放
- 车机视频不静音

**PHONE模式**:
- 车机端播放视频（静音）
- 手机端播放视频+音频
- 进度自动同步（每10秒）
- 连接断开自动暂停

**切换时机**:
- 用户手动切换（点击UI按钮）
- 切换时执行同步启动流程

## Code Quality Improvements

### Recent Refactoring (v0.2.0)

**Thread Safety**:
- Changed `mutableMapOf` to `ConcurrentHashMap` in CarWebSocketServer
- Added null safety checks for WebSocket client IDs
- Prevents concurrent modification exceptions

**Code Cleanup**:
- Removed deprecated `syncProgress()` method from AudioOutputController
- Removed calls to deprecated method in ComposeMainActivity and MainActivity
- Progress synchronization now handled by internal timer only

**Unified Logging**:
- Created `utils/Logger.kt` for consistent logging
- Provides methods with optional dividers for better log readability
- Can be used to replace repetitive logging code

**Configuration Persistence**:
- Added preferences-based IP address storage in HarmonyOS app
- Car IP address now persists across app restarts
- User can modify and save car IP address

**Type Safety**:
- Fixed ArkTS compiler errors in HarmonyOS app
- Added explicit type annotations for HTTP options
- Resolved import issues

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
- [x] **Synchronized startup flow (play_and_seek + resume)**
- [x] **Progress synchronization (every 10 seconds, 2-second threshold)**
- [x] **Disconnection protection (auto-pause on phone)**
- [x] **Thread safety improvements (ConcurrentHashMap)**
- [x] **Code cleanup and quality improvements**

### 🔬 Researched (Not Implemented)
- [ ] Bluetooth audio output (system restriction - requires system-level whitelist)
- [ ] SCO audio channel (blocked by AudioService permission)

### 🚧 In Development
- [ ] Multi-device support (multiple phones connected simultaneously)
- [ ] Advanced audio routing (A2DP, USB audio)
- [ ] Playlist management
- [ ] More video format support

## Development Notes

### Recent Changes (v0.2.0)
- Implemented WebSocket bidirectional communication
- Added synchronized startup flow for audio output separation
- Implemented progress synchronization (10-second interval)
- Added disconnection protection (auto-pause on phone)
- Fixed thread safety issues (ConcurrentHashMap)
- Removed deprecated code (syncProgress method)
- Created unified Logger utility
- Added configuration persistence for car IP address
- Fixed ArkTS compiler errors in HarmonyOS app
- Updated documentation

### Known Limitations
- **Bluetooth Audio**: System restriction prevents third-party apps from using A2DP audio output on target hardware. Solved by pushing audio to phone via WebSocket.
- **Display ID**: Hardcoded to `displayId = 2` for specific hardware. May need adjustment for different devices.
- **Package Name**: Still uses `com.example.floatingscreencasting` (should be changed for production)
- **Single Phone Support**: Currently only supports one phone connected at a time

### Future Enhancements
- Change package name to production domain
- Add more screen ratio options
- Implement multi-phone support
- Add playlist management
- Support more video formats
- Improve error handling and user feedback
- Add automatic device discovery without manual IP configuration
