# FloatingScreenCasting - 车载DLNA投屏应用

## 项目简介

FloatingScreenCasting 是一款专为车载双显示系统设计的DLNA投屏接收应用。它允许用户通过手机将视频投屏到车载驾驶屏，同时在中控屏提供完整的播放控制功能。

### 核心特性

- 📱 **DLNA投屏接收** - 兼容主流手机投屏协议
- 🖥️ **双屏显示** - 中控屏控制 + 驾驶屏视频浮窗
- 🎬 **视频缓存** - 500MB缓存空间，支持离线观看
- ⏯️ **继续观看** - 自动保存播放进度，断点续播
- 🎮 **播放控制** - 播放/暂停/停止/进度调节/静音
- 🔄 **进度同步** - 与手机投屏界面实时同步播放进度
- 📺 **多屏支持** - 支持选择不同的投屏屏幕
- 🚗 **方向盘按键** - 支持媒体按键控制

---

## 功能详解

### 1. DLNA投屏功能

#### 功能描述
应用实现了完整的DLNA DMR（Digital Media Renderer）协议，可以接收来自手机、平板等设备的投屏请求。

#### 实现方法

**SSDP设备发现**
- 使用UDP多播在239.255.255.250:1900端口广播设备存在
- 伪装成"小米电视"设备，提高兼容性
- 定期发送NOTIFY消息保持在线状态

```kotlin
// SsdpServer.kt
private fun sendNotify() {
    val notifyMessage = buildNotifyMessage()
    val packet = DatagramPacket(
        notifyMessage.toByteArray(),
        notifyMessage.length,
        InetAddress.getByName("239.255.255.250"),
        1900
    )
    socket.send(packet)
}
```

**HTTP控制服务器**
- 监听49152端口接收控制命令
- 支持播放、暂停、停止、Seek等命令
- 实现GetPositionInfo实现进度同步

**URL签名处理**
- 自动修复Bilibili视频URL签名（WBI算法）
- 支持爱奇艺等平台的签名处理
- XML实体反转义处理

---

### 2. 双屏显示系统

#### 功能描述
利用Android Presentation API在第二屏幕（驾驶屏）显示视频浮窗，主屏幕（中控屏）提供控制界面。

#### 实现方法

**Presentation窗口**
```kotlin
class VideoPresentation(context: Context, display: Display) : Presentation(context, display) {
    // 使用FLAG_NOT_TOUCHABLE允许触摸穿透
    // 支持位置、大小、透明度实时调节
}
```

**屏幕检测与选择**
- 自动检测系统所有可用Display
- 默认选择Display ID 2（驾驶屏）
- 支持用户手动切换投屏屏幕

**窗口属性控制**
- 位置（X/Y坐标）
- 大小（宽度/高度）
- 透明度（0-100%）
- 支持自定义屏幕比例

---

### 3. 视频缓存系统

#### 功能描述
使用ExoPlayer的Cache功能缓存已观看的视频，支持离线观看和快速加载。

#### 实现方法

**缓存配置**
```kotlin
class VideoCacheManager {
    private val cache: Cache = SimpleCache(
        cacheDir,
        LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024), // 500MB
        StandaloneDatabaseProvider(context)
    )
}
```

**缓存数据源**
- 使用CacheDataSource包装HTTP数据源
- 支持在线/离线自动切换
- LRU策略自动清理旧缓存

**缓存操作**
- 自动缓存播放过的视频
- 支持手动清空缓存
- 获取缓存大小和列表

---

### 4. 继续观看功能

#### 功能描述
自动记录用户的播放历史和进度，支持断点续播和快速恢复上次的观看。

#### 实现方法

**数据持久化**
```kotlin
data class PlaybackRecord(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long
)
```

**进度保存**
- 播放开始时保存记录
- 每10秒自动更新播放进度
- 播放结束时更新最终进度

**UI展示**
- 首页显示继续观看卡片
- 显示视频标题和观看进度
- 点击即可恢复播放

---

### 5. 播放控制系统

#### 功能描述
提供完整的视频播放控制功能，包括播放控制、进度调节、音量控制等。

#### 实现方法

**ExoPlayer集成**
```kotlin
exoPlayer = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
    .setAudioAttributes(audioAttributes, true)
    .build()
```

**播放控制**
- 播放/暂停切换
- 停止播放
- 上一集/下一集
- Seek跳转
- 静音控制

**进度同步**
- 定期获取ExoPlayer的播放位置
- 通过DLNA GetPositionInfo响应同步到手机
- 支持进度条拖动调节

**媒体按键**
- 重写onKeyDown处理媒体按键
- 支持方向盘多功能按键
- KEYCODE_MEDIA_PLAY_PAUSE等

---

## 技术架构

### 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.0.21 | 主要开发语言 |
| Jetpack Compose | 1.7.5 | UI框架 |
| Material 3 | 1.3.0 | 设计系统 |
| Media3 ExoPlayer | 1.5.1 | 视频播放 |
| Coroutines | 1.10.1 | 异步处理 |
| EventBus | 3.3.1 | 事件通信 |
| NanoHTTPD | 2.3.1 | HTTP服务器 |

### 项目结构

```
com.example.floatingscreencasting/
├── cache/                      # 缓存模块
│   └── VideoCacheManager.kt   # 视频缓存管理
├── dlna/                      # DLNA模块
│   ├── DlnaDmrService.kt      # DMR服务管理
│   ├── DlnaHttpServer.kt      # HTTP控制服务器
│   ├── SsdpServer.kt          # SSDP设备发现
│   ├── BilibiliWbiSigner.kt   # Bilibili签名
│   └── IqiyiSigner.kt         # 爱奇艺签名
├── history/                   # 历史记录模块
│   └── PlaybackHistoryManager.kt
├── presentation/              # Presentation模块
│   └── VideoPresentation.kt   # 驾驶屏浮窗
├── ui/                        # UI模块
│   ├── ComposeMainActivity.kt # Compose主界面
│   ├── PreferencesManager.kt  # 设置管理
│   └── composable/            # Compose组件
│       ├── ModernCastingControlCard.kt
│       ├── ModernPlaybackControlCard.kt
│       ├── ModernSettingsCard.kt
│       └── ContinueWatchingCard.kt
```

---

## 使用指南

### 基本使用

1. **启动应用** - 应用启动后自动在驾驶屏打开浮窗
2. **手机投屏** - 在手机视频应用中选择投屏到"小米电视"
3. **播放控制** - 在中控屏使用播放控制按钮
4. **调节浮窗** - 通过设置滑块调整位置、大小、透明度

### 高级功能

**切换投屏屏幕**
1. 打开"悬浮窗控制"卡片
2. 点击"投屏屏幕"下拉菜单
3. 选择目标屏幕

**继续观看**
1. 播放视频时会自动保存进度
2. 下次启动应用会在首页显示"继续观看"卡片
3. 点击卡片即可恢复播放

**调节浮窗属性**
1. 使用"位置"滑块调整X/Y坐标
2. 使用"大小"滑块调整窗口尺寸
3. 使用"透明度"滑块调整窗口透明度
4. 支持"16:9"、"4:3"、"竖屏"、"自定义"比例

---

## 系统要求

- **最低版本**: Android 11 (API Level 30)
- **目标版本**: Android 13 (API Level 36)
- **硬件要求**:
  - 双显示系统（中控屏 + 驾驶屏）
  - ARMv7或ARM64架构
  - 500MB可用存储空间（用于缓存）

---

## 注意事项

1. **权限要求**
   - SYSTEM_ALERT_WINDOW - 悬浮窗权限
   - INTERNET - 网络访问
   - ACCESS_WIFI_STATE - WiFi状态

2. **网络要求**
   - 车机与手机需在同一局域网
   - 建议使用5GHz WiFi以获得更好的性能

3. **已知限制**
   - 投屏视频默认静音（车机系统限制）
   - 某些视频平台可能有防盗链保护
   - 驾驶屏浮窗不可触摸（设计如此）

---

## 开发与构建

### 构建步骤

```bash
# 克隆项目
git clone <repository-url>

# 构建Debug APK
./gradlew assembleDebug

# 构建Release APK
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

### 依赖配置

项目使用Gradle版本目录管理依赖：

```toml
[versions]
media3 = "1.5.1"
compose-bom = "2024.10.01"
kotlin = "2.0.21"
```

---

## 更新日志

### v1.1 (当前版本)

**新增**
- ✨ 应用启动时自动打开浮窗
- ✨ 视频缓存功能（500MB）
- ✨ 继续观看功能
- ✨ DLNA进度同步
- ✨ 屏幕选择下拉菜单

**优化**
- 🎨 全新的Compose Material 3 UI
- 🔧 修复播放控制线程阻塞问题
- 🔧 修复暂停后再播放的问题
- 🔧 优化滑块交互体验

**移除**
- 🗑️ 移除蓝牙功能

---

## 许可证

本项目仅供学习和个人使用。

---

## 联系方式

如有问题或建议，请通过GitHub Issues反馈。
