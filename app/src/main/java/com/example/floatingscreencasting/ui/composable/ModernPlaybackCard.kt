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
@Composable
fun ModernPlaybackControlCard(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isMuted: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onSeek: (Float) -> Unit,
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
                StatusIndicator(isPlaying = isPlaying)
            }

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
                onPlayPause = onPlayPause,
                onStop = onStop,
                onPrevious = onPrevious,
                onNext = onNext,
                onMute = onMute
            )
        }
    }
}

/**
 * 状态指示器
 */
@Composable
private fun StatusIndicator(isPlaying: Boolean) {
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(scale)
                .background(
                    color = if (isPlaying) Success else SurfaceVariant,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isPlaying) "播放中" else "已暂停",
            style = MaterialTheme.typography.bodySmall,
            color = if (isPlaying) Success else OnSurfaceVariant
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
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit
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
            icon = if (isPlaying) "⏸" else "▶",
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

        // 静音（统一风格）
        ModernControlButton(
            onClick = onMute,
            icon = if (isMuted) "🔇" else "🔊",
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
    size: Dp
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
                color = OnSurfaceVariant
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
