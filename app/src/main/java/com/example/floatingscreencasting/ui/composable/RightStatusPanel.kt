package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 右侧状态显示区容器（自适应宽度）
 * 包含所有信息展示和状态显示
 */
@Composable
fun RightStatusPanel(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    castingStatus: String,
    isWindowVisible: Boolean,
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowHeight: Int,
    windowAlpha: Float,
    aspectRatio: AspectRatio,
    selectedDisplayId: Int,
    connectedPhoneDevice: String?,
    phoneDeviceCount: Int,
    hasContinueWatching: Boolean,
    lastPlayedTitle: String,
    lastPlayedProgress: Int,
    onSeek: (Long) -> Unit,
    onContinueWatching: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 播放信息区
        PlaybackInfoSection(
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            castingStatus = castingStatus,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. 详细信息区
        DetailedInfoSection(
            isWindowVisible = isWindowVisible,
            windowX = windowX,
            windowY = windowY,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            windowAlpha = windowAlpha,
            aspectRatio = aspectRatio,
            selectedDisplayId = selectedDisplayId,
            connectedPhoneDevice = connectedPhoneDevice,
            phoneDeviceCount = phoneDeviceCount,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. 继续观看区（如果有）
        if (hasContinueWatching) {
            ContinueWatchingSection(
                title = lastPlayedTitle,
                progress = lastPlayedProgress,
                onContinueWatching = onContinueWatching,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 播放信息区（180dp高）
 */
@Composable
private fun PlaybackInfoSection(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    castingStatus: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "当前播放",
        modifier = modifier
    ) {
        // 视频标题
        Text(
            text = castingStatus,
            style = MaterialTheme.typography.bodyLarge,
            color = GoldOnSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            maxLines = 2,
            minLines = 1
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 播放状态指示
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // 状态指示灯
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (isPlaying) Success else GoldSurfaceVariant,
                        CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isPlaying) "播放中" else "已暂停",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) Success else GoldOnSurfaceVariant,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 进度条
        Column {
            // 时间显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldOnSurface,
                    fontSize = 15.sp
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldOnSurfaceVariant,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 进度滑块
            ModernSlider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 进度百分比
            val progress = if (duration > 0) {
                (currentPosition * 100 / duration).toInt()
            } else {
                0
            }

            Text(
                text = "$progress%",
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * 详细信息区（300dp高）
 */
@Composable
private fun DetailedInfoSection(
    isWindowVisible: Boolean,
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowHeight: Int,
    windowAlpha: Float,
    aspectRatio: AspectRatio,
    selectedDisplayId: Int,
    connectedPhoneDevice: String?,
    phoneDeviceCount: Int,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "详细信息",
        modifier = modifier
    ) {
        // 窗口信息
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "窗口信息",
                style = MaterialTheme.typography.titleSmall,
                color = GoldPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            InfoRow("大小", "${windowWidth} × ${windowHeight} px")
            InfoRow("位置", "($windowX, $windowY)")
            InfoRow("比例", aspectRatio.displayName)
            InfoRow("透明度", "${(windowAlpha * 100).toInt()}%")
            InfoRow("状态", if (isWindowVisible) "已显示" else "已隐藏")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 设备信息
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "设备信息",
                style = MaterialTheme.typography.titleSmall,
                color = GoldPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            InfoRow("屏幕", "Display $selectedDisplayId (驾驶屏)")
            InfoRow("WebSocket", "Port 9999")
            InfoRow("IP地址", "192.168.1.100")

            // 连接状态
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "连接状态:",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldOnSurfaceVariant,
                    fontSize = 14.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (phoneDeviceCount > 0) Success else GoldSurfaceVariant,
                                CircleShape
                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (phoneDeviceCount > 0) {
                            if (connectedPhoneDevice != null) "$connectedPhoneDevice" else "已连接"
                        } else {
                            "未连接"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (phoneDeviceCount > 0) Success else GoldOnSurfaceVariant,
                        fontSize = 14.sp
                    )

                    if (phoneDeviceCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "($phoneDeviceCount 设备)",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldOnSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 继续观看区（150dp高）
 */
@Composable
private fun ContinueWatchingSection(
    title: String,
    progress: Int,
    onContinueWatching: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "继续观看",
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = GoldOnSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1
            )

            // 进度条
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "观看进度",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldOnSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldPrimary,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 进度条可视化
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(GoldSurfaceVariant, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress / 100f)
                            .height(6.dp)
                            .background(GoldPrimary, RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 继续播放按钮
            Surface(
                onClick = onContinueWatching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp),
                color = GoldPrimary.copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 16.sp,
                        color = OnGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "继续播放",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnGold,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
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
 * 信息行
 */
@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = GoldOnSurfaceVariant,
            fontSize = 14.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = GoldOnSurface,
            fontSize = 14.sp
        )
    }
}

/**
 * 格式化时间
 */
private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
