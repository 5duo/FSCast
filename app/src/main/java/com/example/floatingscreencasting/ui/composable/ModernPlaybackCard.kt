package com.example.floatingscreencasting.ui.composable

import androidx.compose.animation.core.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 现代化播放控制卡片
 * 使用渐变背景和更好的视觉层次
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernPlaybackControlCard(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isMuted: Boolean,
    audioOutputMode: String = "speaker",
    connectedPhoneDevice: String? = null,
    phoneDeviceCount: Int = 0,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onSeek: (Float) -> Unit,
    onAudioOutputChange: () -> Unit = {},
    onScanDevices: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ModernCard(modifier = modifier) {
        Column {
            // 标题和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放控制",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )
                // 播放状态指示器
                StatusIndicator(isPlaying = isPlaying, hasContent = duration > 0)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 音频输出选择器
            AudioOutputSelector(
                currentMode = audioOutputMode,
                connectedDevice = connectedPhoneDevice,
                deviceCount = phoneDeviceCount,
                webSocketConnected = phoneDeviceCount > 0,  // 是否有WebSocket客户端连接
                onModeChange = onAudioOutputChange,
                onScanDevices = onScanDevices
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 进度条
            ModernProgressBar(
                currentPosition = currentPosition,
                duration = duration,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 播放控制按钮
            ModernPlaybackControls(
                isPlaying = isPlaying,
                isMuted = isMuted,
                audioOutputMode = audioOutputMode,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onPrevious = onPrevious,
                onNext = onNext,
                onMute = onMute,
                onAudioOutputChange = onAudioOutputChange
            )
        }
    }
}

/**
 * 音频输出选择器
 * 显示当前输出模式和设备状态
 */
@Composable
private fun AudioOutputSelector(
    currentMode: String,
    connectedDevice: String?,
    deviceCount: Int,
    webSocketConnected: Boolean = false,  // WebSocket连接状态
    onModeChange: () -> Unit,
    onScanDevices: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = SurfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：当前输出模式
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (currentMode == "phone") "📱" else "🔊",
                style = MaterialTheme.typography.titleLarge,
                color = if (currentMode == "phone") Color(0xFF6366F1) else OnSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "音频输出",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
                Text(
                    text = if (currentMode == "phone") "FSCast Remote" else "车机扬声器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 中间：设备状态（仅手机模式显示）
        if (currentMode == "phone") {
            if (connectedDevice != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Color(0xFF10B981),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = connectedDevice,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Color(0xFFF59E0B),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "未连接",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF59E0B)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
        }

        // 右侧：切换按钮
        Surface(
            onClick = onModeChange,
            shape = RoundedCornerShape(8.dp),
            color = SurfaceVariant,
            enabled = webSocketConnected  // 只有WebSocket连接时才允许切换
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (currentMode == "phone") "切换到车机" else "切换到手机",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (webSocketConnected) OnSurface else OnSurface.copy(alpha = 0.38f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (webSocketConnected) OnSurfaceVariant else OnSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}

/**
 * 播放状态指示器
 */
@Composable
fun StatusIndicator(isPlaying: Boolean, hasContent: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // 状态文字：根据是否有内容和播放状态决定
    val statusText = when {
        !hasContent -> "已停止"
        isPlaying -> "播放中"
        else -> "已暂停"
    }

    val isActive = hasContent && isPlaying

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(scale)
                .background(
                    color = if (isActive) Success else SurfaceVariant,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) Success else OnSurfaceVariant
        )
    }
}

/**
 * 现代化进度条
 */
@Composable
private fun ModernProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 时间显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "⏱",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                TimeLabel(currentPosition, "当前")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TimeLabel(duration, "总时长")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⏱",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 使用和设置滑块一样的现代化滑块
        ModernSlider(
            value = currentPosition.toFloat(),
            onValueChange = { onSeek(it) },
            valueRange = 0f..duration.toFloat(),
            steps = if (duration > 1000) 1000 else (duration - 1).toInt(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 时间标签
 */
@Composable
private fun TimeLabel(time: Long, label: String) {
    Text(
        text = formatTime(time),
        style = MaterialTheme.typography.titleMedium,
        color = OnSurface,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * 现代化播放控制按钮组
 */
@Composable
private fun ModernPlaybackControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    audioOutputMode: String,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onAudioOutputChange: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一集
        ModernControlButton(
            onClick = onPrevious,
            icon = "⏮",
            contentDescription = "上一集",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 播放/暂停（统一风格）
        ModernControlButton(
            onClick = onPlayPause,
            icon = if (isPlaying) "❚❚" else "▶",
            contentDescription = if (isPlaying) "暂停" else "播放",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 停止
        ModernControlButton(
            onClick = onStop,
            icon = "■",
            contentDescription = "停止",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 下一集
        ModernControlButton(
            onClick = onNext,
            icon = "⏭",
            contentDescription = "下一集",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 音频输出切换
        ModernControlButton(
            onClick = onAudioOutputChange,
            icon = if (audioOutputMode == "phone") "📱" else "🔊",
            contentDescription = if (audioOutputMode == "phone") "输出到手机" else "输出到车机",
            size = 50.dp,
            tint = if (audioOutputMode == "phone") Color(0xFF6366F1) else OnSurfaceVariant
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 静音（统一风格）
        ModernControlButton(
            onClick = onMute,
            icon = if (isMuted) "🔇" else "🔈",
            contentDescription = if (isMuted) "取消静音" else "静音",
            size = 50.dp
        )
    }
}

/**
 * 标准控制按钮
 * 纯灰色背景
 */
@Composable
private fun ModernControlButton(
    onClick: () -> Unit,
    icon: String,
    contentDescription: String?,
    size: Dp,
    tint: Color = OnSurfaceVariant  // 添加tint参数，默认为灰色
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = SurfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = icon,
                fontSize = 24.sp,
                color = tint
            )
        }
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
