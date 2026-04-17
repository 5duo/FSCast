package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onAudioOutputChange: () -> Unit,
    onCenterClick: () -> Unit,
    onMaximizeClick: () -> Unit,
    onDefaultClick: () -> Unit,
    onCustomClick: () -> Unit,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onPositionXChange: (Int) -> Unit,
    onPositionYChange: (Int) -> Unit,
    onSizeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onDisplayChange: (Int) -> Unit,
    onRestartWebSocket: () -> Unit,
    onScanDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(400.dp)
            .fillMaxHeight()
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 播放控制区
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

        // 2. 快速操作区
        QuickActionSection(
            onCenterClick = onCenterClick,
            onMaximizeClick = onMaximizeClick,
            onCustomClick = onCustomClick,
            onDefaultClick = onDefaultClick,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. 窗口调整区
        WindowAdjustSection(
            aspectRatio = aspectRatio,
            windowX = windowX,
            windowY = windowY,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            windowAlpha = windowAlpha,
            onAspectRatioChange = onAspectRatioChange,
            onPositionXChange = onPositionXChange,
            onPositionYChange = onPositionYChange,
            onSizeChange = onSizeChange,
            onHeightChange = onHeightChange,
            onAlphaChange = onAlphaChange,
            modifier = Modifier.fillMaxWidth()
        )

        // 4. 服务控制区
        ServiceControlSection(
            selectedDisplayId = selectedDisplayId,
            availableDisplays = availableDisplays,
            onDisplayChange = onDisplayChange,
            onRestartWebSocket = onRestartWebSocket,
            onScanDevices = onScanDevices,
            modifier = Modifier.fillMaxWidth()
        )
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
                icon = "⏮",
                contentDescription = "上一集",
                size = 45.dp,
                modifier = Modifier.size(45.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 播放/暂停（中心大按钮）
            ControlIconButton(
                onClick = onPlayPause,
                icon = if (isPlaying) "⏸" else "▶",
                contentDescription = if (isPlaying) "暂停" else "播放",
                size = 60.dp,
                modifier = Modifier.size(60.dp),
                backgroundColor = GoldPrimary
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 停止
            ControlIconButton(
                onClick = onStop,
                icon = "■",
                contentDescription = "停止",
                size = 50.dp,
                modifier = Modifier.size(50.dp),
                backgroundColor = Error
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 下一集
            ControlIconButton(
                onClick = onNext,
                icon = "⏭",
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
                icon = if (isMuted) "🔇" else "🔊",
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
                icon = "🎯",
                label = "居中",
                onClick = onCenterClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                icon = "📐",
                label = "最大化",
                onClick = onMaximizeClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                icon = "💾",
                label = "保存",
                onClick = onCustomClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                icon = "🔄",
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
            icon = "📏",
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
            icon = "↔️",
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
            icon = "↕️",
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
            icon = "🔆",
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
 * 服务控制区（200dp高）
 */
@Composable
private fun ServiceControlSection(
    selectedDisplayId: Int,
    availableDisplays: List<DisplayInfo>,
    onDisplayChange: (Int) -> Unit,
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
                icon = "🔌",
                label = "重启WebSocket服务",
                onClick = onRestartWebSocket,
                modifier = Modifier.fillMaxWidth()
            )

            // 扫描手机设备按钮
            ServiceActionButton(
                icon = "🔍",
                label = "扫描手机设备",
                onClick = onScanDevices,
                modifier = Modifier.fillMaxWidth()
            )

            // 屏幕选择下拉菜单
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
                            Text(
                                text = "🖥️",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = availableDisplays.find { it.id == selectedDisplayId }?.name ?: "Display $selectedDisplayId",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GoldOnSurface
                            )
                        }
                        Text(
                            text = "▼",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldOnSurfaceVariant
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
                                    display.name,
                                    color = GoldOnSurface
                                )
                            },
                            onClick = {
                                onDisplayChange(display.id)
                                expanded = false
                            },
                            leadingIcon = {
                                if (display.id == selectedDisplayId) {
                                    Text(
                                        text = "✓",
                                        color = GoldPrimary
                                    )
                                }
                            }
                        )
                    }
                }
            }
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
 * 控制图标按钮
 */
@Composable
private fun ControlIconButton(
    onClick: () -> Unit,
    icon: String,
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
            Text(
                text = icon,
                fontSize = (size.value * 0.4).toInt().sp,
                color = GoldOnSurface
            )
        }
    }
}

/**
 * 快速操作按钮
 */
@Composable
private fun QuickActionButton(
    icon: String,
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
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 24.sp
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
 * 服务操作按钮
 */
@Composable
private fun ServiceActionButton(
    icon: String,
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
            Text(
                text = icon,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 18.sp
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
