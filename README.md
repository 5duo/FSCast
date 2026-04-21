# FSCast (FloatingScreenCasting)

<div align="center">

**DLNA单系统多屏幕投屏接收器应用**

[![Android](https://img.shields.io/badge/Android-11%2B-blue.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.7.6-2DD4C8.svg)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-1.5.1-green.svg)](https://developer.android.com/media)
[![HarmonyOS](https://img.shields.io/badge/HarmonyOS-Next-black.svg)](https://www.harmonyos.com)

基于Android Presentation API的单系统多屏幕DLNA投屏接收器，支持中控屏控制界面与副屏悬浮窗播放，支持音频输出分离到手机端，专为车载双屏娱乐系统设计。

</div>

---

## 📖 项目简介

FSCast是一个基于Android Presentation API的单系统多屏幕DLNA投屏接收器应用。它运行在单个Android系统上，利用Android的多屏幕显示能力，在不同的物理屏幕上展示不同的内容：

- **主屏幕（中控屏）**: DLNA控制界面，提供播放控制、进度调节、窗口设置、音频输出选择等功能
- **副屏幕（驾驶屏）**: 视频播放窗口，显示DLNA投屏的多媒体内容（静音状态）
- **手机端（FSCast Remote）**: 播放完整的视频+音频，与车机端进度实时同步

本应用专为车载双屏娱乐系统设计，完美适配需要在不同屏幕上分别显示控制界面和视频内容的场景，同时解决了车机蓝牙音频输出的限制。

### 核心特性
- ✅ DLNA DMR完整实现，支持主流投屏协议
- ✅ Android Presentation API多屏幕显示
- ✅ Jetpack Compose + Material 3现代化UI
- ✅ Media3 ExoPlayer高性能视频播放
- ✅ iOS风格视觉设计
- ✅ 实时悬浮窗位置/大小/透明度调节
- ✅ **音频输出分离**：车机画面+手机声音
- ✅ **WebSocket双向通信**：车机与手机实时同步
- ✅ **B站反爬虫支持**：HTTP头完整传递链路
- ✅ **倍速平滑同步**：基于VideoTogether算法，1x/1.25x/1.75x/2.0x自动追赶
- ✅ **时间戳对齐**：补偿系统时间差异和网络延迟
- ✅ **原源模式**：拦截Stop命令，自动跳过B站广告
- ✅ **设备自动发现**：UDP广播发现手机设备
- ✅ **断线自动暂停**：手机端连接断开自动暂停播放

### 📺 双屏显示
- **中控屏（主屏）**: iOS风格控制界面
  - 实时播放控制（播放/暂停/停止）
  - 进度条拖动和时间显示
  - 上一集/下一集快速切换
  - 悬浮窗位置、大小、透明度实时调节
  - 静音控制和音频状态显示
  - **音频输出切换**：车机扬声器 / FSCast Remote手机端 / 原源模式（跳过B站广告）

- **驾驶屏（副屏）**: 悬浮视频窗口
  - 自动显示DLNA投屏内容
  - 非FLAG_NOT_FOCUSABLE设计，支持触控
  - 可调节窗口位置和尺寸
  - 支持多种屏幕比例（16:9/4:3/竖屏）
  - **音频输出分离时自动静音**

### 📱 手机端配合（FSCast Remote）
- **鸿蒙版**（推荐）：HarmonyOS Next应用
- **Android版**：Android 11+应用
- **核心功能**：
  - WebSocket客户端：与车机双向通信
  - 完整视频播放：视频+音频输出
  - 倍速平滑同步：与车机端保持一致
  - 时间戳对齐：补偿系统时间差异和网络延迟
  - 断线自动暂停：连接断开时自动暂停播放
  - HTTP头支持：完整传递B站反爬虫验证
  - 后台播放：支持通知栏和锁屏媒体控制

### 🎬 DLNA投屏支持
- 完整的DLNA DMR（Digital Media Renderer）实现
- SSDP设备发现协议
- HTTP控制服务器
- 支持主流投屏协议（小米、华为、Bilibili等）
- AvTransport媒体传输控制
- RenderingControl渲染控制
- **B站反爬虫支持**：完整HTTP头传递链路

### 🎮 播放控制
- **基础控制**: 播放、暂停、停止
- **进度控制**: 实时进度条拖动，DLNA进度同步
- **快捷操作**: 上一集、下一集、静音
- **方向盘支持**: 媒体按键映射（KEYCODE_MEDIA_PLAY_PAUSE等）
- **音频输出切换**: 车机扬声器 ↔ 手机端

### 🎨 UI设计
- **iOS风格视觉设计**
- **Jetpack Compose + Material 3**
- 流畅的动画和过渡效果
- 清晰的视觉层次和信息架构
- 深色/浅色主题适配

### 🚀 高级功能
- **视频缓存**: 支持视频预加载
- **继续观看**: 记录播放进度，断点续播
- **自动开窗**: DLNA连接时自动显示悬浮窗
- **多格式支持**: MP4、MKV、AVI等主流格式
- **H.264/H.265解码**: 硬件加速解码
- **音频输出分离**: 车机画面（静音）+手机声音
- **倍速平滑同步**: 1x/1.25x/1.75x/2.0x自动追赶进度
- **时间戳对齐**: 补偿系统时间差异和网络延迟
- **原源模式**: 拦截Stop命令，自动跳过B站广告
- **断线保护**: 手机端连接断开自动暂停

### 🔊 音频输出分离功能

由于车机系统限制第三方应用无法使用蓝牙A2DP输出，FSCast通过WebSocket将视频推送到手机端播放：

**工作原理**：
1. 车机端接收DLNA投屏，在驾驶屏播放视频（自动静音）
2. 用户点击"切换到手机"按钮
3. 车机端通过WebSocket发送视频URL和HTTP头到手机
4. 手机端加载视频并跳转到当前进度，然后暂停等待
5. 车机端发送"同时播放"命令，两端同步开始播放
6. 车机端每10秒同步一次进度到手机端

**同步启动流程**：
```
车机端（暂停） → 发送play_and_seek → 手机端加载并跳转
                ↓
            等待2秒
                ↓
车机端发送resume → 两端同时播放
```

**进度同步机制（v0.3.2倍速平滑同步）**：
- 基于VideoTogether时间戳对齐算法
- 车机端每10秒发送带时间戳的进度更新
- 手机端补偿系统时间差异和网络延迟
- 根据进度差异自动选择同步策略：
  - 差异<1秒：正常播放（1.0x）
  - 差异1-3秒：微调追赶（1.25x）
  - 差异3-10秒：中等追赶（1.75x）
  - 差异10-20秒：高速追赶（2.0x）
  - 差异>20秒：强制seek对齐

**原源模式（v0.3.2新增）**：
- 用于B站投屏时跳过广告
- 拦截DLNA Stop命令，暂停4秒后自动恢复播放
- 适合B站切换剧集时的广告场景

**断线保护**：
- 手机端检测到WebSocket连接断开，立即暂停播放
- 避免音频继续播放而画面停止
- 车机端也会检测连接状态，更新UI显示

---

## 🏗️ 技术架构

### 技术栈
**车机端**:
- **语言**: Kotlin 2.0.21
- **UI框架**: Jetpack Compose 1.7.6 + Material 3 1.3.1
- **视频播放**: Media3 ExoPlayer 1.5.1
- **网络**: OkHttp 4.12.0 + Java-WebSocket
- **事件总线**: EventBus 3.3.1
- **并发**: Kotlin Coroutines + Flow

**手机端（鸿蒙）**:
- **语言**: ArkTS
- **UI框架**: ArkUI
- **视频播放**: @ohos.multimedia.media
- **网络**: @kit.NetworkKit（WebSocket + HTTP）
- **存储**: @kit.ArkData（Preferences）

### 核心架构
```
┌─────────────────────────────────────────────────────┐
│              FSCast 生态系统                         │
├─────────────────────────────────────────────────────┤
│  车机端 (FSCast)                                    │
│  ┌─────────────────────────────────────────────┐   │
│  │  ComposeMainActivity                       │   │
│  │  - ModernCastingControlCard                │   │
│  │  - ModernPlaybackCard                      │   │
│  │  - AudioOutputSelector                     │   │
│  └─────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────┐   │
│  │  VideoPresentation (驾驶屏)                 │   │
│  │  - ExoPlayer Video View (静音)             │   │
│  │  - Floating Window                         │   │
│  └─────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────┐   │
│  │  AudioOutputController                      │   │
│  │  - 切换音频输出模式                         │   │
│  │  - 进度同步（每10秒）                       │   │
│  │  - WebSocket命令发送                        │   │
│  └─────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────┐   │
│  │  CarWebSocketServer (端口9999)              │   │
│  │  - WebSocket服务器                          │   │
│  │  - 客户端连接管理                           │   │
│  │  - 消息广播                                 │   │
│  └─────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────┐   │
│  │  DlnaDmrService                             │   │
│  │  - SSDP Device Discovery                   │   │
│  │  - HTTP Control Server                     │   │
│  │  - AvTransport/RenderControl               │   │
│  └─────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────┤
│  手机端 (FSCast Remote - 鸿蒙)                    │
│  ┌─────────────────────────────────────────────┐   │
│  │  Index (主页面)                             │   │
│  │  - DLNA DMR服务                             │   │
│  │  - VideoPlayer (视频+音频)                 │   │
│  │  - 进度同步显示                             │   │
│  └─────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────┐   │
│  │  CarWebSocketClient                         │   │
│  │  - 连接到车机WebSocket                      │   │
│  │  - 接收播放命令                             │   │
│  │  - 发送状态更新                             │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### 双屏实现
使用Android **Presentation API** 实现双屏显示：

```kotlin
// 创建副屏Presentation
val presentation = VideoPresentation(this, display)
presentation.show()
```

**关键设计**:
- 主屏用于控制界面，保持交互响应
- 副屏用于视频播放，支持触控操作
- Display ID硬编码为2（适配目标硬件）

---

## 📋 系统要求

### 开发环境
**车机端**:
- **Android Studio**: Hedgehog (2023.1.1) 或更高版本
- **JDK**: 17 或更高版本
- **Android SDK**: API 30-36
- **Gradle**: 8.5

**手机端（鸿蒙）**:
- **DevEco Studio**: 5.0.0 或更高版本
- **HarmonyOS SDK**: API 12+
- **Node.js**: 16.x 或更高版本

### 运行环境
**车机端**:
- **Android版本**: 11.0 (API 30) 或更高
- **屏幕要求**: 双屏显示系统
- **网络**: Wi-Fi连接
- **存储**: 至少100MB可用空间

**手机端**:
- **鸿蒙版**: HarmonyOS NEXT
- **Android版**: Android 11 或更高
- **网络**: Wi-Fi连接（与车机同一网络）

---

## 🚀 快速开始

### 克隆项目

```bash
git clone https://github.com/5duo/FSCast.git
cd FSCast
```

### 构建车机端

```bash
# 编译Debug版本
./gradlew assembleDebug

# 编译Release版本
./gradlew assembleRelease

# 安装到连接的设备
./gradlew installDebug
```

### 构建手机端（鸿蒙）

```bash
cd companion-harmony

# 编译HAP
cmd.exe //c "C:\\command-line-tools\\hvigor\\bin\\hvigorw.bat assembleHap --mode module -p module=entry@default -p product=default"

# 安装到鸿蒙设备
C:/command-line-tools/sdk/default/openharmony/toolchains/hdc.exe install entry/build/default/outputs/default/entry-default-signed.hap
```

---

## 📱 使用说明

### 1. 启动车机端应用
安装并启动应用后，应用会自动：
- 启动DLNA DMR服务（端口7676）
- 启动WebSocket服务器（端口9999）
- 开始监听投屏请求
- 准备显示控制界面

### 2. 启动手机端应用
- 打开FSCast Remote（鸿蒙版）
- 应用自动连接到车机端（默认IP：192.168.200.47）
- 连接成功后显示"已连接"状态

### 3. 手机投屏
确保手机和车机连接同一Wi-Fi网络：

1. 打开手机视频APP（如小米视频、华为视频、B站等）
2. 找到投屏图标
3. 选择"FSCast"设备
4. 开始投屏

### 4. 切换音频输出
投屏成功后：
1. 在车机端点击"切换到手机"按钮
2. 车机端视频自动静音
3. 手机端自动加载并播放视频
4. 两端进度自动同步

### 5. 控制播放
在车机端中控屏可以：
- 点击播放/暂停按钮控制视频
- 拖动进度条跳转播放位置
- 点击上一集/下一集切换视频
- 调整悬浮窗位置和大小
- 切换音频输出（车机/手机）

---

## 🔧 配置说明

### Display ID配置
如果车机的驾驶屏Display ID不是2，需要修改：

```kotlin
// ComposeMainActivity.kt
private val drivingDisplayId = 2  // 修改为你的Display ID
```

查看Display ID方法：
```bash
adb shell dumpsys display | grep "mDisplayId="
```

### 车机IP地址配置
手机端默认连接到192.168.200.47，如需修改：

**鸿蒙版**：
- 配置会自动保存到preferences
- 首次连接后可以手动修改
- 修改后自动持久化

### 屏幕方向
应用默认横屏显示，如需修改：

```xml
<!-- AndroidManifest.xml -->
android:screenOrientation="sensorLandscape"
```

---

## 📚 技术文档

详细的技术文档和实现说明请查看 [docs/](./docs/) 目录：

- **[DLNA发现机制详解](./docs/DLNA_DISCOVERY_MECHANISM.md)** - SSDP协议工作原理
- **[音频路由解决方案](./docs/audio_routing_solution.md)** - 车载音频输出研究
- **[WebSocket通信协议](./docs/WEBSOCKET_PROTOCOL.md)** - 车机与手机双向通信协议（新增）

---

## 📂 项目结构

本项目包含两个应用：

| 应用 | 目录 | 平台 | 说明 |
|------|------|------|------|
| **FSCast** | `app/` | Android (车机) | DLNA投屏接收器 + 视频播放 + WebSocket服务器 |
| **FSCast Remote** | `companion-harmony/` | HarmonyOS | 手机端视频播放 + WebSocket客户端 |

```
FSCast/
├── app/                              # 车机端 Android App
│   ├── src/main/java/.../
│   │   ├── audio/                    # 音频路由
│   │   ├── cache/                    # 视频缓存
│   │   ├── dlna/                     # DLNA DMR服务
│   │   │   ├── AudioOutputController.kt    # 音频输出控制器
│   │   │   ├── CarWebSocketServer.kt       # WebSocket服务器
│   │   │   ├── DlnaDmcClient.kt           # DLNA DMC客户端
│   │   │   └── PhoneDeviceManager.kt      # 手机设备管理
│   │   ├── presentation/             # 副屏视频显示
│   │   ├── ui/                       # 主屏 Compose UI
│   │   ├── websocket/                # WebSocket通信
│   │   └── utils/                    # 工具类
│   └── build.gradle.kts
│
├── companion-harmony/                # 手机端鸿蒙 App (FSCast Remote)
│   └── entry/src/main/ets/
│       ├── service/                  # DLNA + WebSocket服务
│       │   ├── CarWebSocketClient.ets      # WebSocket客户端
│       │   ├── DlnaHttpServer.ets          # DLNA HTTP服务器
│       │   ├── VideoPlayer.ets             # 视频播放器
│       │   └── SsdpServer.ets              # SSDP设备发现
│       └── pages/                    # ArkUI 页面
│
├── docs/                             # 技术文档
├── gradle/                           # Gradle配置
├── CLAUDE.md                         # Claude Code开发指南
├── README.md                         # 项目说明
└── .gitignore
```

---

## 🤝 贡献指南

欢迎贡献代码、报告Bug或提出新功能建议！

### 贡献流程
1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

### 代码规范
- 遵循Kotlin/ArkTS代码规范
- 使用有意义的变量和函数命名
- 添加必要的注释
- 保持代码简洁和可读性

---

## 📄 许可证

本项目仅供学习和研究使用。请勿用于商业用途。

---

## 🙏 致谢

感谢以下开源项目：
- [ExoPlayer](https://github.com/google/ExoPlayer) - 强大的媒体播放库
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代化UI框架
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) - WebSocket客户端库

特别感谢：
- **涛神**（新版悬浮球作者）- 感谢您的指导和带领，带我们飞！🚀

---

## 📞 联系方式

- **作者**: 5duo
- **邮箱**: 370582811@qq.com
- **GitHub**: [@5duo](https://github.com/5duo)

---

## 📝 更新日志

### v0.3.2 (2026-04-21)
- ✨ 新增原源模式，拦截Stop命令并暂停4秒后恢复（跳过B站广告）
- ✨ 实现VideoTogether风格的时间戳对齐算法
- ✨ 新增倍速平滑同步（1x/1.25x/1.75x/2.0x自动追赶）
- ✨ WebSocket消息添加时间戳字段用于网络延迟补偿
- 🎨 鸿蒙端UI重构为控制台风格
- 🎨 新增车机端进度显示卡片
- 🎨 新增实时日志显示功能
- 🔧 优化进度同步精度，减少画面卡顿
- 🐛 修复B站切换剧集时广告中断投屏的问题

### v0.2.0 (2026-04-17)
- ✨ 实现音频输出分离功能（车机画面+手机声音）
- ✨ 添加WebSocket双向通信机制
- ✨ 实现进度自动同步（每10秒检查）
- ✨ 添加B站反爬虫HTTP头完整传递链路
- ✨ 实现设备自动发现（UDP广播）
- ✨ 添加断线自动暂停保护
- 🐛 修复线程安全问题（ConcurrentHashMap）
- 🐛 删除废弃代码（syncProgress方法）
- 📝 完善项目文档
- 🔧 代码质量优化和清理

### v0.1.0-beta (2026-04-15)
- ✨ 完成Jetpack Compose + Material 3 UI迁移
- ✨ 实现完整的DLNA DMR功能
- ✨ 支持双屏显示和悬浮窗
- ✨ 添加视频缓存和继续观看功能
- ✨ iOS风格视觉设计
- 🐛 修复DLNA进度同步问题
- 📝 完善项目文档

---

<div align="center">

**如果这个项目对你有帮助，请给个⭐️Star支持一下！**

Made with ❤️ by 5duo

</div>
