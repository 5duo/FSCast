package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

/**
 * 播放控制卡片
 * 包含进度条和播放控制按钮
 */
@Composable
fun PlaybackControlCard(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isMuted: Boolean,
    localAudioTest: Boolean,  // 本地音频测试模式
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onToggleLocalAudio: () -> Unit,  // 切换本地音频测试
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    IosCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = "播放控制",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 进度条
            PlaybackProgressBar(
                currentPosition = currentPosition,
                duration = duration,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 播放控制按钮
            PlaybackControls(
                isPlaying = isPlaying,
                isMuted = isMuted,
                localAudioTest = localAudioTest,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onPrevious = onPrevious,
                onNext = onNext,
                onMute = onMute,
                onToggleLocalAudio = onToggleLocalAudio
            )
        }
    }
}

/**
 * 进度条组件
 */
@Composable
private fun PlaybackProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 时间显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 进度滑块
        val progress = if (duration > 0) {
            currentPosition.toFloat() / duration.toFloat()
        } else {
            0f
        }

        IosSlider(
            value = progress,
            onValueChange = { onSeek(it * duration) },
            valueRange = 0f..1f,
            steps = 1000,  // 1000步 = 0.1%精度
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 播放控制按钮组 - 使用文字标签
 */
@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    localAudioTest: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onToggleLocalAudio: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一集
        ControlButton(
            onClick = onPrevious,
            text = "◀◀",
            modifier = Modifier.size(50.dp),
            contentDescription = "上一集"
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 播放/暂停
        ControlButton(
            onClick = onPlayPause,
            text = if (isPlaying) "⏸" else "▶️",
            modifier = Modifier.size(50.dp),
            contentDescription = "播放/暂停"
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 停止
        ControlButton(
            onClick = onStop,
            text = "⏹",
            modifier = Modifier.size(50.dp),
            contentDescription = "停止"
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 下一集
        ControlButton(
            onClick = onNext,
            text = "▶▶",
            modifier = Modifier.size(50.dp),
            contentDescription = "下一集"
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 静音
        ControlButton(
            onClick = onMute,
            text = if (isMuted) "🔇" else "🔊",
            modifier = Modifier.size(50.dp),
            contentDescription = "静音",
            tint = if (isMuted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 本地音频测试
        ControlButton(
            onClick = onToggleLocalAudio,
            text = "🔊🚗",
            modifier = Modifier.size(50.dp),
            contentDescription = "本地音频测试",
            fontSize = 14.sp,
            tint = if (localAudioTest) {
                Color(0xFF10B981)  // 绿色表示测试模式开启
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 控制按钮组件 - 使用emoji图标
 */
@Composable
private fun ControlButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String?,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                color = tint
            )
        }
    }
}

/**
 * 格式化时间显示
 * 将秒数转换为 MM:SS 或 HH:MM:SS 格式
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
