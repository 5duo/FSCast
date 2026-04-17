package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 左侧操作区容器（400dp宽）
 * 包含所有驾驶员高频操作按钮
 */
@Composable
fun LeftOperationPanel(
    isPlaying: Boolean,
    isMuted: Boolean,
    currentPosition: Long,
    duration: Long,
    aspectRatio: AspectRatio,
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowHeight: Int,
    windowAlpha: Float,
    selectedDisplayId: Int,
    availableDisplays: List<DisplayInfo>,
    isFloatingWindowEnabled: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onAudioOutputChange: () -> Unit,
    onToggleWindow: () -> Unit,
    onCenterClick: () -> Unit,
    onMaximizeClick: () -> Unit,
    onDefaultClick: () -> Unit,
    onCustomClick: () -> Unit,
    onDisplayChange: (Int) -> Unit,
    onOpenSettingsPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(400.dp)
            .fillMaxHeight()
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // 1. 播放控制区（保持不变）
        PlaybackControlSection(
            isPlaying = isPlaying,
            isMuted = isMuted,
            onPlayPause = onPlayPause,
            onStop = onStop,
            onPrevious = onPrevious,
            onNext = onNext,
            onMute = onMute,
            onAudioOutputChange = onAudioOutputChange,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. 悬浮窗控制区（新重构）
        FloatingWindowControlSection(
            isFloatingWindowEnabled = isFloatingWindowEnabled,
            windowX = windowX,
            windowY = windowY,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            windowAlpha = windowAlpha,
            aspectRatio = aspectRatio,
            selectedDisplayId = selectedDisplayId,
            availableDisplays = availableDisplays,
            onToggleWindow = onToggleWindow,
            onDisplayChange = onDisplayChange,
            onDefaultClick = onDefaultClick,
            onMaximizeClick = onMaximizeClick,
            onCustomClick = onCustomClick,
            onOpenSettingsPanel = onOpenSettingsPanel,
            modifier = Modifier.fillMaxWidth()
        )
        }
    }
}

/**
 * 播放控制区（200dp高）
 */
@Composable
private fun PlaybackControlSection(
    isPlaying: Boolean,
    isMuted: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onAudioOutputChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "播放控制",
        modifier = modifier
    ) {
        // 播放控制按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一集
            ControlIconButton(
                onClick = onPrevious,
                imageVector = MaterialIconsRes.SKIP_PREVIOUS,
                contentDescription = "上一集",
                size = 45.dp,
                modifier = Modifier.size(45.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 播放/暂停（中心大按钮）
            ControlIconButton(
                onClick = onPlayPause,
                imageVector = if (isPlaying) MaterialIconsRes.PAUSE else MaterialIconsRes.PLAY,
                contentDescription = if (isPlaying) "暂停" else "播放",
                size = 60.dp,
                modifier = Modifier.size(60.dp),
                backgroundColor = GoldPrimary
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 停止
            ControlIconButton(
                onClick = onStop,
                imageVector = MaterialIconsRes.STOP,
                contentDescription = "停止",
                size = 50.dp,
                modifier = Modifier.size(50.dp),
                backgroundColor = Error
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 下一集
            ControlIconButton(
                onClick = onNext,
                imageVector = MaterialIconsRes.SKIP_NEXT,
                contentDescription = "下一集",
                size = 45.dp,
                modifier = Modifier.size(45.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 第二行：静音 + 音频切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 静音按钮
            ControlIconButton(
                onClick = onMute,
                imageVector = if (isMuted) MaterialIconsRes.VOLUME_MUTE else MaterialIconsRes.VOLUME_UP,
                contentDescription = if (isMuted) "取消静音" else "静音",
                size = 40.dp,
                modifier = Modifier.size(40.dp)
            )

            // 音频输出切换按钮
            Surface(
                onClick = onAudioOutputChange,
                modifier = Modifier
                    .height(40.dp)
                    .weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = GoldSurfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🔊 ↔ 📱",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "音频切换",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldOnSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 快速操作区（180dp高）
 */
@Composable
private fun QuickActionSection(
    onCenterClick: () -> Unit,
    onMaximizeClick: () -> Unit,
    onCustomClick: () -> Unit,
    onDefaultClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "快速操作",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                imageVector = MaterialIconsRes.ALIGN_CENTER,
                label = "居中",
                onClick = onCenterClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                imageVector = MaterialIconsRes.FULL_SCREEN,
                label = "最大化",
                onClick = onMaximizeClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                imageVector = MaterialIconsRes.SAVE,
                label = "保存",
                onClick = onCustomClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                imageVector = MaterialIconsRes.REFRESH,
                label = "默认",
                onClick = onDefaultClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 窗口调整区（300dp高）
 */
@Composable
private fun WindowAdjustSection(
    aspectRatio: AspectRatio,
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowHeight: Int,
    windowAlpha: Float,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onPositionXChange: (Int) -> Unit,
    onPositionYChange: (Int) -> Unit,
    onSizeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onAlphaChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "窗口调整",
        modifier = modifier
    ) {
        // 比例选择
        Text(
            text = "屏幕比例",
            style = MaterialTheme.typography.bodySmall,
            color = GoldOnSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatio.values().forEach { ratio ->
                AspectRatioButton(
                    ratio = ratio,
                    selected = aspectRatio == ratio,
                    onClick = { onAspectRatioChange(ratio) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 大小滑块
        ModernSliderRow(
            imageVector = MaterialIconsRes.RESIZE,
            label = "窗口大小",
            value = windowWidth,
            valueRange = 240..1920,
            onValueChange = { onSizeChange(it) },
            suffix = "px",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 位置X滑块
        ModernSliderRow(
            imageVector = MaterialIconsRes.ALIGN_HORIZONTAL,
            label = "水平位置",
            value = windowX,
            valueRange = 0..1920,
            onValueChange = { onPositionXChange(it) },
            suffix = "px",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 位置Y滑块
        ModernSliderRow(
            imageVector = MaterialIconsRes.ALIGN_VERTICAL,
            label = "垂直位置",
            value = windowY,
            valueRange = 0..720,
            onValueChange = { onPositionYChange(it) },
            suffix = "px",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 透明度滑块
        ModernSliderRow(
            imageVector = MaterialIconsRes.VISIBILITY,
            label = "透明度",
            value = (windowAlpha * 100).toInt(),
            valueRange = 10..100,
            onValueChange = { onAlphaChange(it / 100f) },
            suffix = "%",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 服务控制区（简化版）
 */
@Composable
private fun ServiceControlSection(
    onRestartWebSocket: () -> Unit,
    onScanDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "服务控制",
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 重启WebSocket服务按钮
            ServiceActionButton(
                imageVector = MaterialIconsRes.REFRESH,
                label = "重启WebSocket服务",
                onClick = onRestartWebSocket,
                modifier = Modifier.fillMaxWidth()
            )

            // 扫描手机设备按钮
            ServiceActionButton(
                imageVector = MaterialIconsRes.SCAN,
                label = "扫描手机设备",
                onClick = onScanDevices,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== 辅助组件 ====================

/**
 * 分区卡片容器
 */
@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GoldSurface.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GoldOnSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

/**
 * 控制图标按钮（使用Material Icons）
 */
@Composable
private fun ControlIconButton(
    onClick: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = GoldSurfaceVariant
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = backgroundColor.copy(alpha = 0.8f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            MaterialIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                iconSize = size * 0.5f,
                tint = GoldOnSurface
            )
        }
    }
}

/**
 * 快速操作按钮（使用Material Icons）
 */
@Composable
private fun QuickActionButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(8.dp),
        color = GoldSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MaterialIcon(
                imageVector = imageVector,
                contentDescription = label,
                iconSize = 24.dp,
                tint = GoldOnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 悬浮窗控制区（新重构）
 */
@Composable
private fun FloatingWindowControlSection(
    isFloatingWindowEnabled: Boolean,
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowHeight: Int,
    windowAlpha: Float,
    aspectRatio: AspectRatio,
    selectedDisplayId: Int,
    availableDisplays: List<DisplayInfo>,
    onToggleWindow: () -> Unit,
    onDisplayChange: (Int) -> Unit,
    onDefaultClick: () -> Unit,
    onMaximizeClick: () -> Unit,
    onCustomClick: () -> Unit,
    onOpenSettingsPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "悬浮窗控制",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 悬浮窗开关
            WindowToggleButton(
                isEnabled = isFloatingWindowEnabled,
                onToggle = onToggleWindow
            )

            // 屏幕选择下拉菜单
            ScreenSelectorDropdown(
                selectedDisplayId = selectedDisplayId,
                availableDisplays = availableDisplays,
                onDisplayChange = onDisplayChange
            )

            // 快捷操作按钮组
            AdjustmentPanelButtons(
                onDefaultClick = onDefaultClick,
                onMaximizeClick = onMaximizeClick,
                onCustomClick = onCustomClick
            )

            // 屏幕设置按钮
            ScreenSettingsButton(
                onClick = onOpenSettingsPanel
            )
        }
    }
}

/**
 * 悬浮窗开关按钮
 */
@Composable
private fun WindowToggleButton(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isEnabled) {
            GoldPrimary.copy(alpha = 0.8f)
        } else {
            GoldSurfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            MaterialIcon(
                imageVector = if (isEnabled) MaterialIconsRes.VISIBILITY else MaterialIconsRes.DELETE,
                contentDescription = if (isEnabled) "悬浮窗已启用" else "悬浮窗已禁用",
                iconSize = 18.dp,
                tint = if (isEnabled) OnGold else GoldOnSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isEnabled) "悬浮窗已启用" else "悬浮窗已禁用",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEnabled) OnGold else GoldOnSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 屏幕选择下拉菜单
 */
@Composable
private fun ScreenSelectorDropdown(
    selectedDisplayId: Int,
    availableDisplays: List<DisplayInfo>,
    onDisplayChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Text(
        text = "投屏屏幕",
        style = MaterialTheme.typography.bodySmall,
        color = GoldOnSurfaceVariant,
        fontSize = 14.sp
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = GoldSurfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialIcon(
                        imageVector = MaterialIconsRes.DISPLAY,
                        contentDescription = "投屏屏幕",
                        iconSize = 16.dp,
                        tint = GoldOnSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Display $selectedDisplayId",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoldOnSurface
                    )
                }
                MaterialIcon(
                    imageVector = MaterialIconsRes.DOWN,
                    contentDescription = "展开",
                    iconSize = 14.dp,
                    tint = GoldOnSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(GoldSurface, RoundedCornerShape(8.dp))
                .width(300.dp)
        ) {
            availableDisplays.forEach { display ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "Display ${display.id}",
                            color = GoldOnSurface
                        )
                    },
                    onClick = {
                        onDisplayChange(display.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (display.id == selectedDisplayId) {
                            MaterialIcon(
                                imageVector = MaterialIconsRes.CHECK,
                                contentDescription = "已选中",
                                iconSize = 16.dp,
                                tint = GoldPrimary
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * 快捷操作按钮组（替换详细调整面板）
 */
@Composable
private fun AdjustmentPanelButtons(
    onDefaultClick: () -> Unit,
    onMaximizeClick: () -> Unit,
    onCustomClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "快捷设置",
            style = MaterialTheme.typography.bodySmall,
            color = GoldOnSurfaceVariant,
            fontSize = 14.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                imageVector = MaterialIconsRes.REFRESH,
                label = "默认",
                onClick = onDefaultClick,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                imageVector = MaterialIconsRes.FULL_SCREEN,
                label = "最大化",
                onClick = onMaximizeClick,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                imageVector = MaterialIconsRes.SAVE,
                label = "自定义",
                onClick = onCustomClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 调整按钮（使用Material Icons）
 */
@Composable
private fun AdjustmentButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(8.dp),
        color = GoldSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MaterialIcon(
                imageVector = imageVector,
                contentDescription = label,
                iconSize = 16.dp,
                tint = GoldOnSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurface,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 比例按钮
 */
@Composable
private fun AspectRatioButton(
    ratio: AspectRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        GoldPrimary
    } else {
        GoldSurfaceVariant
    }

    val textColor = if (selected) {
        OnGold
    } else {
        GoldOnSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ratio.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 屏幕设置按钮
 */
@Composable
private fun ScreenSettingsButton(
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = GoldSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            MaterialIcon(
                imageVector = MaterialIconsRes.SETTINGS,
                contentDescription = "屏幕设置",
                iconSize = 18.dp,
                tint = GoldOnSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "屏幕设置",
                style = MaterialTheme.typography.bodyMedium,
                color = GoldOnSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
        }
    }
}

/**
 * 服务操作按钮（使用Material Icons）
 */
@Composable
private fun ServiceActionButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = GoldSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            MaterialIcon(
                imageVector = imageVector,
                contentDescription = label,
                iconSize = 18.dp,
                tint = GoldOnSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = GoldOnSurface,
                fontSize = 15.sp
            )
        }
    }
}
