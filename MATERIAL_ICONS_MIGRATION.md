# Material Icons 集成说明

## 问题分析
IconPark的outline主题使用stroke属性，Android vector drawable对stroke支持有限，导致图标显示不正确。

## 解决方案
使用Google官方的Material Design Icons，这些图标专为Android设计，质量更好。

## 安装步骤

### 1. 在项目 build.gradle 中添加依赖
```gradle
dependencies {
    implementation "androidx.compose.material:material-icons-extended:1.7.6"
}
```

### 2. 在代码中使用
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
// ... 其他图标

@Composable
fun MyScreen() {
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "播放",
        tint = GoldOnSurface
    )
}
```

### 3. 图标映射表
- display: Icons.Default.Tv
- play: Icons.Default.PlayArrow
- pause: Icons.Default.Pause
- volume_up: Icons.Default.VolumeUp
- connection: Icons.Default.Wifi
- bluetooth: Icons.Default.Bluetooth
- close: Icons.Default.Close
- eye: Icons.Default.Visibility
- eye_off: Icons.Default.VisibilityOff
- delete: Icons.Default.Delete
- positioning: Icons.Default.PinDrop
- resize: Icons.Default.AspectRatio
- stop: Icons.Default.Stop
- skip_previous: Icons.Default.SkipPrevious
- skip_next: Icons.Default.SkipNext
- volume_mute: Icons.Default.VolumeOff
- center: Icons.Default.CenterFocusStrong
- fullscreen: Icons.Default.Fullscreen
- save: Icons.Default.Save
- refresh: Icons.Default.Refresh
- scan: Icons.Default.QrCodeScanner
- restart: Icons.Default.RestartAlt
- horizontal: Icons.Default.SwapHoriz
- vertical: Icons.Default.SwapVert
- down: Icons.Default.ArrowDownward
- up: Icons.Default.ArrowUpward
- left: Icons.Default.ArrowBack
- right: Icons.Default.ArrowForward
- check: Icons.Default.Check
- home: Icons.Default.Home
- setting: Icons.Default.Settings

## 优势
- ✅ Google官方支持，质量有保证
- ✅ 专为Android设计，显示效果最佳
- ✅ Compose原生支持，使用简单
- ✅ 黑金配色系统完美兼容
- ✅ 无需手动下载SVG文件

## 迁移步骤
1. 添加依赖
2. 将所有 IconParkIcon() 调用替换为 Icon(imageVector = ...)
3. 删除drawable中的SVG文件
4. 删除IconParkRes.kt文件
