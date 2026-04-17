package com.example.floatingscreencasting.ui.composable

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 主控制网格
 * 左侧：视频预览，右侧：大型播放控制按钮
 */
@Composable
fun MainControlGrid(
    isPlaying: Boolean,
    isMuted: Boolean,
    currentAudioOutput: String,
    castingStatus: String,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onMute: () -> Unit,
    onAudioOutputChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 左侧：视频预览区域
            VideoPreviewArea(
                isPlaying = isPlaying,
                castingStatus = castingStatus,
                modifier = Modifier.weight(1f)
            )

            // 右侧：播放控制按钮组
            PlaybackControlsGroup(
                isPlaying = isPlaying,
                isMuted = isMuted,
                currentAudioOutput = currentAudioOutput,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onMute = onMute,
                onAudioOutputChange = onAudioOutputChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 视频预览区域
 */
@Composable
private fun VideoPreviewArea(
    isPlaying: Boolean,
    castingStatus: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        SurfaceVariant,
                        Surface.copy(alpha = 0.5f)
                    )
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 视频占位符
        if (castingStatus.contains("等待") || castingStatus.contains("已停止")) {
            Text(
                text = "📺",
                style = MaterialTheme.typography.displayLarge,
                fontSize = 48.sp,
                color = OnSurfaceVariant.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "等待投屏",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 18.sp
            )
        } else {
            // 播放中的动画效果
            val infiniteTransition = rememberInfiniteTransition(label = "playing-indicator")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "playing-scale"
            )

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isPlaying) "播放中" else "已暂停",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp
            )
        }
    }
}

/**
 * 播放控制按钮组
 */
@Composable
private fun PlaybackControlsGroup(
    isPlaying: Boolean,
    isMuted: Boolean,
    currentAudioOutput: String,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onMute: () -> Unit,
    onAudioOutputChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 中心大按钮：播放/暂停
        LargePlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPause
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 环绕按钮组
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 停止按钮
            ControlButton(
                icon = "⏹",
                label = "停止",
                onClick = onStop,
                modifier = Modifier.size(60.dp),
                color = Error
            )

            // 静音按钮
            ControlButton(
                icon = if (isMuted) "🔇" else "🔊",
                label = if (isMuted) "已静音" else "静音",
                onClick = onMute,
                modifier = Modifier.size(60.dp),
                color = if (isMuted) Warning else SurfaceVariant
            )

            // 音频输出切换按钮
            ControlButton(
                icon = "🎧",
                label = when (currentAudioOutput) {
                    "speaker" -> "扬声器"
                    "phone" -> "手机"
                    else -> "音频"
                },
                onClick = onAudioOutputChange,
                modifier = Modifier.size(60.dp),
                color = if (currentAudioOutput == "phone") Primary else SurfaceVariant
            )
        }
    }
}

/**
 * 大型播放/暂停按钮
 */
@Composable
private fun LargePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "play-button-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "play-button-scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(100.dp)
            .scale(scale),
        shape = CircleShape,
        color = Primary,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶",
                style = MaterialTheme.typography.displayLarge,
                fontSize = 48.sp,
                color = Color.White
            )
        }
    }
}

/**
 * 通用控制按钮
 */
@Composable
private fun ControlButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = color.copy(alpha = 0.2f),
            modifier = Modifier.size(50.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface,
            fontSize = 11.sp
        )
    }
}
