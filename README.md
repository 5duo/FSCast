# FSCast (FloatingScreenCasting)

<div align="center">

**DLNA单系统多屏幕投屏接收器应用**

[![Android](https://img.shields.io/badge/Android-11%2B-blue.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.7.6-2DD4C8.svg)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Media3-1.5.1-green.svg)](https://developer.android.com/media)

基于Android Presentation API的单系统多屏幕DLNA投屏接收器，支持中控屏控制界面与副屏悬浮窗播放，专为车载双屏娱乐系统设计。

</div>

---

## 📖 项目简介

FSCast是一个基于Android Presentation API的单系统多屏幕DLNA投屏接收器应用。它运行在单个Android系统上，利用Android的多屏幕显示能力，在不同的物理屏幕上展示不同的内容：

- **主屏幕（中控屏）**: DLNA控制界面，提供播放控制、进度调节、窗口设置等功能
- **副屏幕（驾驶屏）**: 视频播放窗口，显示DLNA投屏的多媒体内容

本应用专为车载双屏娱乐系统设计，完美适配需要在不同屏幕上分别显示控制界面和视频内容的场景。

### 核心特性
- ✅ DLNA DMR完整实现，支持主流投屏协议
- ✅ Android Presentation API多屏幕显示
- ✅ Jetpack Compose + Material 3现代化UI
- ✅ Media3 ExoPlayer高性能视频播放
- ✅ iOS风格视觉设计
- ✅ 实时悬浮窗位置/大小/透明度调节

### 📺 双屏显示
- **中控屏（主屏）**: iOS风格控制界面
  - 实时播放控制（播放/暂停/停止）
  - 进度条拖动和时间显示
  - 上一集/下一集快速切换
  - 悬浮窗位置、大小、透明度实时调节
  - 静音控制和音频状态显示

- **驾驶屏（副屏）**: 悬浮视频窗口
  - 自动显示DLNA投屏内容
  - 非`FLAG_NOT_FOCUSABLE`设计，支持触控
  - 可调节窗口位置和尺寸
  - 支持多种屏幕比例（16:9/4:3/竖屏）

### 🎬 DLNA投屏支持
- 完整的DLNA DMR（Digital Media Renderer）实现
- SSDP设备发现协议
- HTTP控制服务器
- 支持主流投屏协议（小米、华为、Bilibili等）
- AvTransport媒体传输控制
- RenderingControl渲染控制

### 🎮 播放控制
- **基础控制**: 播放、暂停、停止
- **进度控制**: 实时进度条拖动，DLNA进度同步
- **快捷操作**: 上一集、下一集、静音
- **方向盘支持**: 媒体按键映射（KEYCODE_MEDIA_PLAY_PAUSE等）

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

---

## 🏗️ 技术架构

### 技术栈
- **语言**: Kotlin 2.0.21
- **UI框架**: Jetpack Compose 1.7.6 + Material 3 1.3.1
- **视频播放**: Media3 ExoPlayer 1.5.1
- **网络**: OkHttp 4.12.0
- **事件总线**: EventBus 3.3.1
- **并发**: Kotlin Coroutines + Flow
- **依赖注入**: 手动依赖注入（暂未使用Hilt）

### 核心架构
```
┌─────────────────────────────────────────────┐
│           FSCast Application                 │
├─────────────────────────────────────────────┤
│  Primary Display (中控屏)                    │
│  ┌─────────────────────────────────────┐   │
│  │  ComposeMainActivity               │   │
│  │  - ModernCastingControlCard        │   │
│  │  - PlaybackControlCard             │   │
│  │  - ModernSettingsCard              │   │
│  └─────────────────────────────────────┘   │
├─────────────────────────────────────────────┤
│  Secondary Display (驾驶屏)                  │
│  ┌─────────────────────────────────────┐   │
│  │  VideoPresentation                 │   │
│  │  - ExoPlayer Video View            │   │
│  │  - Floating Window                 │   │
│  └─────────────────────────────────────┘   │
├─────────────────────────────────────────────┤
│  DLNA Service Layer                         │
│  ┌─────────────────────────────────────┐   │
│  │  DlnaDmrService                     │   │
│  │  - SSDP Device Discovery           │   │
│  │  - HTTP Control Server             │   │
│  │  - AvTransport/RenderControl       │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
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
- **Android Studio**: Hedgehog (2023.1.1) 或更高版本
- **JDK**: 17 或更高版本
- **Android SDK**: API 30-36
- **Gradle**: 8.5

### 运行环境
- **Android版本**: 11.0 (API 30) 或更高
- **屏幕要求**: 双屏显示系统
- **网络**: Wi-Fi连接
- **存储**: 至少100MB可用空间
- **权限**: 
  - 网络权限
  - 系统窗口权限（悬浮窗）
  - 前台服务权限

---

## 🚀 快速开始

### 克隆项目

```bash
git clone https://github.com/5duo/FSCast.git
cd FSCast
```

### 构建项目

```bash
# 编译Debug版本
./gradlew assembleDebug

# 编译Release版本
./gradlew assembleRelease

# 安装到连接的设备
./gradlew installDebug
```

### 运行测试

```bash
# 单元测试
./gradlew test

# 集成测试（需要连接设备/模拟器）
./gradlew connectedAndroidTest
```

---

## 📱 使用说明

### 1. 启动应用
安装并启动应用后，应用会自动：
- 启动DLNA DMR服务
- 开始监听投屏请求
- 准备显示控制界面

### 2. 手机投屏
确保手机和车机连接同一Wi-Fi网络：

1. 打开手机视频APP（如小米视频、华为视频、B站等）
2. 找到投屏图标
3. 选择"FSCast"设备
4. 开始投屏

### 3. 控制播放
在中控屏可以：
- 点击播放/暂停按钮控制视频
- 拖动进度条跳转播放位置
- 点击上一集/下一集切换视频
- 调整悬浮窗位置和大小
- 点击静音按钮控制音量

### 4. 驾驶屏观看
视频会自动在驾驶屏显示为悬浮窗：
- 支持触控操作
- 可拖动调整位置
- 可缩放调整大小

---

## 🔧 配置说明

### Display ID配置
如果车机的驾驶屏Display ID不是2，需要修改：

```kotlin
// VideoPresentation.kt
private val displayId = 2  // 修改为你的Display ID
```

查看Display ID方法：
```bash
adb shell dumpsys display | grep "mDisplayId="
```

### 屏幕方向
应用默认横屏显示，如需修改：

```xml
<!-- AndroidManifest.xml -->
android:screenOrientation="sensorLandscape"
```

可选值：
- `sensorLandscape` - 传感器横屏（默认）
- `landscape` - 强制横屏
- `portrait` - 强制竖屏
- `fullSensor` - 全传感器模式

---

## 📚 技术文档

详细的技术文档和实现说明请查看 [docs/](./docs/) 目录：

- **[DLNA发现机制详解](./docs/DLNA_DISCOVERY_MECHANISM.md)** - SSDP协议工作原理
- **[代理移除指南](./docs/PROXY_REMOVAL_GUIDE.md)** - DLNA代理设置移除
- **[B站WBI签名实现](./docs/BILIBILI_WBI_IMPLEMENTATION.md)** - Bilibili对接方案
- **[音频路由解决方案](./docs/audio_routing_solution.md)** - 车载音频输出研究

## 📂 项目结构

```
FSCast/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/floatingscreencasting/
│   │       │   ├── dlna/                    # DLNA服务
│   │       │   │   ├── DlnaDmrService.kt   # DMR服务
│   │       │   │   ├── SSDPServer.kt       # SSDP发现
│   │       │   │   └── HttpServer.kt       # HTTP控制
│   │       │   ├── presentation/            # 副屏显示
│   │       │   │   └── VideoPresentation.kt # 视频悬浮窗
│   │       │   ├── ui/                      # 主屏UI
│   │       │   │   ├── ComposeMainActivity.kt
│   │       │   │   └── composable/          # Compose组件
│   │       │   │       ├── ModernCastingControlCard.kt
│   │       │   │       ├── PlaybackControlCard.kt
│   │       │   │       ├── ModernSettingsCard.kt
│   │       │   │       └── ModernSlider.kt
│   │       │   └── utils/                   # 工具类
│   │       ├── res/                         # 资源文件
│   │       └── AndroidManifest.xml          # 应用清单
│   └── build.gradle.kts                     # 应用构建配置
├── frida/                                   # Frida脚本（研究用）
├── memory/                                  # 研究文档
├── gradle/                                  # Gradle配置
├── build.gradle.kts                         # 项目构建配置
├── CLAUDE.md                                # Claude Code指南
├── README.md                                # 项目说明
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
- 遵循Kotlin代码规范
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
- [OkHttp](https://github.com/square/okhttp) - 高效HTTP客户端

特别感谢：
- **涛神**（新版悬浮球作者）- 感谢您的指导和带领，带我们飞！🚀

---

## 📞 联系方式

- **作者**: 5duo
- **邮箱**: 370582811@qq.com
- **GitHub**: [@5duo](https://github.com/5duo)

---

## 📝 更新日志

### v1.0.0 (2026-04-15)
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
