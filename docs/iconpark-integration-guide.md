# IconPark图标集成指南

## 图标映射表

### 状态概览卡片（StatusOverviewCard）

| 当前emoji | IconPark图标名称 | 图标分类 | 下载链接 |
|----------|----------------|---------|---------|
| 📺 投屏状态 | `display` | Base | https://iconpark.oceanengine.com/official/display |
| ▶️ 播放状态 | `play` | Base | https://iconpark.oceanengine.com/official/play |
| 🔊 音频输出 | `volume-up` | Base | https://iconpark.oceanengine.com/official/volume-up |
| 📱 连接状态 | `connection` | Hardware | https://iconpark.oceanengine.com/official/connection |

### 悬浮窗控制区（FloatingWindowControlSection）

| 当前emoji | IconPark图标名称 | 图标分类 | 下载链接 |
|----------|----------------|---------|---------|
| 🖼️ 悬浮窗启用 | `eye-open` | Base | https://iconpark.oceanengine.com/official/eye-open |
| 🚫 悬浮窗禁用 | `delete` | Base | https://iconpark.oceanengine.com/official/delete |
| 🖥️ 屏幕选择 | `display` | Base | https://iconpark.oceanengine.com/official/display |
| 📍 位置调整 | `positioning` | Maps | https://iconpark.oceanengine.com/official/positioning |
| 📏 大小调整 | `resize` | Edit | https://iconpark.oceanengine.com/official/resize |
| 🔆 透明度调整 | `eye` | Base | https://iconpark.oceanengine.com/official/eye |
| 📐 比例调整 | `aspect-ratio` | Edit | https://iconpark.oceanengine.com/official/aspect-ratio |

### 播放控制区（PlaybackControlSection）

| 当前emoji | IconPark图标名称 | 图标分类 | 下载链接 |
|----------|----------------|---------|---------|
| ▶ 播放 | `play` | Base | https://iconpark.oceanengine.com/official/play |
| ⏸ 暂停 | `pause-one` | Base | https://iconpark.oceanengine.com/official/pause-one |
| ■ 停止 | `stop` | Base | https://iconpark.oceanengine.com/official/stop |
| ⏮ 上一集 | `fast-backward` | Base | https://iconpark.oceanengine.com/official/fast-backward |
| ⏭ 下一集 | `fast-forward` | Base | https://iconpark.oceanengine.com/official/fast-forward |
| 🔇 静音 | `volume-mute` | Base | https://iconpark.oceanengine.com/official/volume-mute |
| 🔊 取消静音 | `volume-up` | Base | https://iconpark.oceanengine.com/official/volume-up |

### 快速操作区（QuickActionSection）

| 当前emoji | IconPark图标名称 | 图标分类 | 下载链接 |
|----------|----------------|---------|---------|
| 🎯 居中 | `align-center` | Edit | https://iconpark.oceanengine.com/official/align-center |
| 📐 最大化 | `full-screen` | Base | https://iconpark.oceanengine.com/official/full-screen |
| 💾 保存 | `save` | Base | https://iconpark.oceanengine.com/official/save |
| 🔄 默认 | `refresh` | Base | https://iconpark.oceanengine.com/official/refresh |

### 服务控制区（ServiceControlSection）

| 当前emoji | IconPark图标名称 | 图标分类 | 下载链接 |
|----------|----------------|---------|---------|
| 🔌 重启服务 | `refresh` | Base | https://iconpark.oceanengine.com/official/refresh |
| 🔍 扫描设备 | `scan` | Base | https://iconpark.oceanengine.com/official/scan |

### 位置滑块（PositionAdjustmentContent）

| 当前emoji | IconPark图标名称 | 图标分类 | 下载链接 |
|----------|----------------|---------|---------|
| ↔️ 水平位置 | `align-horizontal` | Edit | https://iconpark.oceanengine.com/official/align-horizontal |
| ↕️ 垂直位置 | `align-vertical` | Edit | https://iconpark.oceanengine.com/official/align-vertical |

### 大小调整（SizeAdjustmentContent）

| 当前emoji | IconPark图标名称 | 图标分类 | 下载链接 |
|----------|----------------|---------|---------|
| 📏 窗口宽度 | `resize` | Edit | https://iconpark.oceanengine.com/official/resize |
| 📐 窗口高度 | `crop` | Edit | https://iconpark.oceanengine.com/official/crop |

### 面板操作

| 当前emoji | IconPark图标名称 | 图标分类 | 下载链接 |
|----------|----------------|---------|---------|
| ✕ 关闭面板 | `close` | Base | https://iconpark.oceanengine.com/official/close |
| ▼ 展开面板 | `down` | Arrows | https://iconpark.oceanengine.com/official/down |

## SVG图标下载步骤

### 方法1：从IconPark网站下载（推荐）

1. 访问 https://iconpark.oceanengine.com/
2. 在搜索框中输入图标名称（如 `display`）
3. 选择图标
4. 点击下载按钮
5. 选择SVG格式
6. **重要**：打开SVG文件，移除`fill`属性以支持着色
   ```xml
   <!-- 移除前 -->
   <path fill="#333" d="..."/>
   
   <!-- 移除后 -->
   <path d="..."/>
   ```

### 方法2：使用IconPark CLI工具

```bash
# 安装IconPark CLI
npm install -g @iconpark/cli

# 下载单个图标
iconpark download display --target ./app/src/main/res/drawable

# 批量下载
iconpark download display play pause-one volume-up --target ./app/src/main/res/drawable
```

## SVG文件放置

将下载的SVG文件重命名并放置到：
```
app/src/main/res/drawable/
├── ic_display.xml
├── ic_play.xml
├── ic_pause_one.xml
├── ic_volume_up.xml
├── ic_volume_mute.xml
└── ...
```

**注意**：Android要求SVG资源文件名必须小写且只包含字母、数字和下划线。

## 代码替换示例

### 替换前（emoji）
```kotlin
Text(
    text = "📺",
    style = MaterialTheme.typography.titleMedium,
    fontSize = 20.sp
)
```

### 替换后（IconPark SVG）
```kotlin
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

Image(
    painter = painterResource(R.drawable.ic_display),
    contentDescription = "投屏状态",
    modifier = Modifier.size(20.dp),
    colorFilter = ColorFilter.tint(GoldOnSurface)
)
```

## 批量替换脚本

创建一个辅助函数来简化图标使用：

```kotlin
// IconParkIcon.kt
@Composable
fun IconParkIcon(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = GoldOnSurface
) {
    Icon(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
```

使用示例：
```kotlin
IconParkIcon(
    resId = R.drawable.ic_display,
    contentDescription = "投屏状态",
    modifier = Modifier.size(20.dp)
)
```

## 实施优先级

### 高优先级（用户体验明显提升）
- 播放控制图标（play, pause, stop, etc.）
- 状态指示图标（display, volume, connection）
- 面板操作图标（close, down）

### 中优先级
- 快速操作图标（align-center, full-screen, save, refresh）
- 调整按钮图标（positioning, resize, eye）

### 低优先级
- 装饰性图标
- 不常用功能图标

## 验证清单

- [ ] SVG图标正确显示
- [ ] 黑金配色着色正常
- [ ] 图标大小符合规范
- [ ] 无编译错误
- [ ] 所有emoji已替换
- [ ] ContentDescription完整（可访问性）
