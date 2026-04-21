# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FSCast is a DLNA Digital Media Renderer (DMR) receiver application that uses Android's Presentation API to display content on multiple screens within a single Android system. It displays a floating video window on a secondary display (driving screen) while providing playback controls on the primary display (central control screen). It also supports audio output separation to a companion phone app via WebSocket communication.

**Application Name**: FSCast (FloatingScreenCasting)

**Current Version**: v0.3.1-dev

**Target Hardware**: In-car dual-display systems or any multi-screen Android system where the secondary display has a fixed Display ID (typically 2).

**Companion App**: FSCast Remote (HarmonyOS) - Receives video from car and plays audio with background playback support

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

### HarmonyOS Phone UI Architecture (v0.3.1+)

The phone app uses a **console-style UI** with modular components:

```
┌─────────────────────────────┐
│     StatusPanel             │  ← Collapsible status display
│   (WebSocket/Network/…)     │
├─────────────────────────────┤
│     VideoPlayerCard         │  ← Unified video player
│   (16:9, HTTP headers)      │
├─────────────────────────────┤
│     VideoUrlCard            │  ← URL display + copy/download
│   (URL/Download/Progress)   │
├─────────────────────────────┤
│     ControlPanel            │  ← Reconnect + IP settings
│   (Connection management)   │
└─────────────────────────────┘
```

**Component Architecture**:
- **StatusPanel**: Collapsible panel showing WebSocket, network, and playback status
- **VideoPlayerCard**: Unified video player with HTTP headers support
- **VideoUrlCard**: Displays received video URL with copy and download functionality
- **ControlPanel**: Reconnect button and IP configuration dialog
- **CarIpDialog**: IP address input dialog with validation and quick selection

**Communication**: Phone端只通过WebSocket与车机端通信，不再有DLNA DMR功能

### WebSocket Communication Architecture

The app uses WebSocket for bidirectional communication between car and phone:

**Important**: Phone端已移除DLNA DMR功能，现在只通过WebSocket与车机端通信。

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
- If difference > 1 second, phone seeks to car's position
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
  - `entry/src/main/ets/pages/Index.ets` - Main page with WebSocket client and video player
  - `entry/src/main/ets/components/StatusPanel.ets` - Collapsible status panel component
  - `entry/src/main/ets/components/VideoPlayerCard.ets` - Unified video player card
  - `entry/src/main/ets/components/VideoUrlCard.ets` - Video URL display with copy and download
  - `entry/src/main/ets/components/ControlPanel.ets` - Bottom control panel with reconnect and IP settings
  - `entry/src/main/ets/components/CarIpDialog.ets` - Car IP configuration dialog
  - `entry/src/main/ets/service/VideoPlayer.ets` - Video player with HTTP headers support
  - `entry/src/main/ets/service/CarWebSocketClient.ets` - WebSocket client for car connection
  - `entry/src/main/ets/service/MediaSessionService.ets` - AVSession integration for background playback
  - `entry/src/main/ets/service/BackgroundService.ets` - Background task management

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
- 手机端VideoPlayer检查差异是否>1秒
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

### HarmonyOS Phone Client Optimization (v0.3.0-dev)

**Background Playback Support**:
- Integrated AVSession for notification bar and lock screen media controls
- Added BackgroundTasks Kit integration for continuous background playback
- System enforces AVSession requirement for background audio playback
- Implemented media metadata updates (title, artist, duration)
- Implemented real-time playback state updates (play/pause/progress)

**Progress Synchronization Optimization**:
- Reduced sync threshold from 2 seconds to 1 second for better synchronization
- Unified sync threshold across car (AudioOutputController) and phone (VideoPlayer)
- Immediate seek response when car drags progress bar

**Network State Monitoring**:
- Added connection state monitoring using Network Kit
- Automatic pause on network loss
- Auto-resume when network becomes available

**State Cleanup**:
- Complete state reset on stop command
- URI cleanup on playback end
- Proper resource release on app exit

**HarmonyOS API Requirements** (Critical):
```typescript
// module.json5 必须配置
{
  "requestPermissions": [{
    "name": "ohos.permission.KEEP_BACKGROUND_RUNNING",
    "reason": "$string:background_reason",
    "usedScene": { "abilities": ["EntryAbility"], "when": "always" }
  }],
  "abilities": [{
    "name": "EntryAbility",
    "backgroundModes": ["audioPlayback"]
  }]
}

// 必须创建和激活AVSession
avSession = await AVSessionManager.createAVSession(context, 'FSCastAudioSession', 'audio')
await avSession.activate()

// 必须设置元数据和播放状态
await avSession.setAVMetadata({ assetId, title, artist, duration })
await avSession.setAVPlaybackState({ state, position })

// 必须申请长时任务
await backgroundTaskManager.startBackgroundRunning(context, BackgroundMode.AUDIO_PLAYBACK, wantAgentObj)
```

**ArkTS Limitations**:
- No `URL` class support - use string parsing for URL operations
- No `any` or `unknown` types allowed
- No `NetConnection.off()` method - listeners auto-cleanup
- Limited PlaybackStrategy options

**Key Files Modified**:
- `companion-harmony/entry/src/main/ets/pages/Index.ets` - Added MediaSession integration
- `companion-harmony/entry/src/main/ets/service/VideoPlayer.ets` - Optimized sync threshold and PlaybackStrategy
- `app/src/main/java/com/example/floatingscreencasting/dlna/AudioOutputController.kt` - Updated sync threshold

## Completed Features

### ✅ Implemented
- [x] DLNA DMR full implementation (SSDP, AvTransport, RenderingControl) - Car side only
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
- [x] **Progress synchronization (every 10 seconds, 1-second threshold)**
- [x] **Disconnection protection (auto-pause on phone)**
- [x] **HarmonyOS background playback (AVSession + BackgroundTasks)**
- [x] **Notification bar and lock screen media controls**
- [x] **Network state monitoring and auto-pause/resume**
- [x] **Complete state cleanup on stop and playback end**
- [x] **Thread safety improvements (ConcurrentHashMap)**
- [x] **Code cleanup and quality improvements**
- [x] **HarmonyOS UI refactoring (console-style, removed DLNA DMR from phone)**
- [x] **Video URL display and copy/download functionality**
- [x] **System Download directory integration**

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

### Recent Changes (v0.3.0-dev)
- **HarmonyOS Background Playback**: Integrated AVSession and BackgroundTasks Kit
- **Progress Sync Optimization**: Reduced threshold from 2s to 1s across both platforms
- **Media Controls**: Added notification bar and lock screen media control support
- **Network Monitoring**: Implemented connection state monitoring for network switching
- **State Management**: Complete cleanup on stop/playback end
- **Metadata Updates**: Real-time playback state and metadata updates
- **UI Refactoring**: Console-style UI with collapsible status panel
- **DLNA DMR Removal**: Removed DLNA DMR services from phone end (SSDP, HTTP server)
- **WebSocket Only**: Phone now only communicates via WebSocket with car
- **Video URL Features**: Added URL display, copy to clipboard, and download to system Download directory
- **Component Architecture**: Created StatusPanel, VideoPlayerCard, VideoUrlCard, ControlPanel, CarIpDialog components
- **Documentation**: Added comprehensive HarmonyOS development knowledge to skill (v2.1.0)

### Known Limitations
- **Bluetooth Audio**: System restriction prevents third-party apps from using A2DP audio output on target hardware. Solved by pushing audio to phone via WebSocket.
- **Display ID**: Hardcoded to `displayId = 2` for specific hardware. May need adjustment for different devices.
- **Package Name**: Still uses `com.example.floatingscreencasting` (should be changed for production)
- **Single Phone Support**: Currently only supports one phone connected at a time
- **Phone UI**: Console-style UI optimized for technical users; may need simplification for general users
- **Download Directory**: Requires `READ_WRITE_DOWNLOAD_DIRECTORY` permission for video downloads

### Future Enhancements
- Change package name to production domain
- Add more screen ratio options
- Implement multi-phone support
- Add playlist management
- Support more video formats
- Improve error handling and user feedback
- Add automatic device discovery without manual IP configuration

## Testing Checklist

### HarmonyOS Background Playback (v0.3.0-dev)

#### Basic Functionality
- [ ] Phone can connect to car via WebSocket
- [ ] Video plays with correct HTTP headers (Bilibili anti-crawler)
- [ ] Audio output switches between car speaker and phone

#### Background Playback
- [ ] Press Home key while playing - audio continues
- [ ] Notification bar shows media control
- [ ] Notification displays correct video title and duration
- [ ] Lock screen shows media card
- [ ] Lock screen controls work (play/pause/seek)
- [ ] Playback state updates in real-time on controls

#### Progress Synchronization
- [ ] Car drags progress bar - phone syncs within 1 second
- [ ] Progress difference < 1 second does not trigger sync
- [ ] Progress difference > 1 second triggers seek
- [ ] Car sends progress every 10 seconds
- [ ] Phone correctly receives and processes progress updates

#### State Cleanup
- [ ] Car sends stop - phone clears URI and returns to idle
- [ ] Video playback ends - both devices exit casting state
- [ ] WebSocket disconnect - phone immediately pauses
- [ ] App exit - all services properly released

#### Network Handling
- [ ] WiFi switch during playback - pauses or continues correctly
- [ ] Network lost - playback pauses
- [ ] Network recovered - playback resumes

#### System Requirements
- [ ] AVSession activated before playback
- [ ] Long task requested when playback starts
- [ ] Background modes configured in module.json5
- [ ] KEEP_BACKGROUND_RUNNING permission granted

## Development Resources

### HarmonyOS Expert Knowledge
- **Skill**: `harmonyos-expert.md` (v2.1.0)
- **Location**: `C:\Users\weiwu\.claude\skills\harmonyos-expert.md`
- **Coverage**: Media Kit, AVSession, BackgroundTasks, Network Kit, ArkUI, ArkTS

### Key HarmonyOS Documentation
1. Media Kit - Video/audio playback with AVPlayer
2. AVSession - Background playback enforcement
3. BackgroundTasks - Long-running background tasks
4. Network Kit - Connection state monitoring
5. Video Component - Simple video playback UI
6. Streaming Media - HLS/HTTP-FLV/DASH support

### Important Notes
- **System Requirement**: Apps MUST use AVSession for background playback (enforced by system)
- **Permission**: KEEP_BACKGROUND_RUNNING required for background audio
- **Configuration**: backgroundModes must include "audioPlayback"
- **ArkTS Limitations**: No URL class, no any/unknown types, limited API surface

### MCP Servers Available

#### search_harmonyos_docs
官方 HarmonyOS 文档查询服务器，用于获取最新的 API 参考和开发指南。

**使用方法**：
- `mcp__harmonyos-docs__search_harmonyos_docs` - 搜索文档
- `mcp__harmonyos-docs__get_harmonyos_doc` - 获取完整文档
- `mcp__harmonyos-docs__list_harmonyos_categories` - 列出文档分类

**使用场景**：
- 查找 API 用法和官方示例
- 了解 HarmonyOS NEXT 新特性
- 解决开发问题（官方文档最准确）

**相关资源**：
- [HarmonyOS Symbol 图标库](https://developer.huawei.com/consumer/cn/design/harmonyos-symbol/)
- [华为开发者联盟](https://developer.huawei.com/consumer/cn/)

**项目记忆**：详见 `memory/harmonyos_docs_mcp.md`
减少常见 LLM 编码错误的行为准则。
根据需要与项目特定指令合并。

**权衡:**这些准则偏向谨慎而非速度。
对于简单任务,请自行判断。

## 1. 编码前先思考

**不要假设。不要隐藏困惑。把权衡摆出来。**

实现之前:

- 明确说出你的假设。如果不确定,就问。
- 如果存在多种理解方式,列出来。
- 如果有更简单的方案,说出来。
- 如果有什么不清楚,停下来。说明哪里困惑。

## 2. 简单优先

**能解决问题的最少代码。不要投机性功能。**

- 不要加没被要求的功能
- 单次使用的代码不要搞抽象
- 不要加没被要求的"灵活性"
- 不要为不可能的场景写错误处理
- 如果 200 行能缩成 50 行,就重写

## 3. 外科手术式修改

**只改必须改的。只清理自己弄乱的。**

- 不要"改进"相邻的代码或格式
- 不要重构没坏的东西
- 匹配现有风格,即使你不喜欢
- 如果发现死代码,提一句——但别删

## 4. 目标驱动执行

**定义成功标准。循环直到验证通过。**

把任务转化为可验证的目标:

- "加个验证" → "写测试,然后让测试通过"
- "修这个 bug" → "用测试复现,然后修复"
- "重构 X" → "确保重构前后测试都通过"