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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 左侧彩蛋面板
 * 在非车机设备上显示，提供额外的信息和控制功能
 * 在车机设备上会被导航栏遮挡，因此不影响正常使用
 */
@Composable
fun EasterEggPanel(
    isPlaying: Boolean = false,
    currentPosition: Long = 0L,
    duration: Long = 0L,
    appVersion: String = "v0.3.1",
    onPlayPause: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 检测是否为非车机设备（简化判断：通过屏幕宽度）
    // 在实际应用中，可以通过配置或设备特性来判断
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isNonCarDevice = screenWidthDp > 600

    if (!isNonCarDevice) {
        // 车机设备：不显示（会被导航栏遮挡）
        Spacer(modifier = modifier.width(150.dp))
        return
    }

    // 非车机设备：显示彩蛋面板
    Column(
        modifier = modifier
            .width(150.dp)
            .fillMaxHeight()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Background.copy(alpha = 0.95f),
                        Background.copy(alpha = 0.98f),
                        Background
                    )
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 顶部：应用Logo和信息
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            // 应用Logo（文字版）
            Text(
                text = "FSCast",
                style = MaterialTheme.typography.titleLarge,
                color = Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = appVersion,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 分隔线
            HorizontalDivider(
                modifier = Modifier
                    .width(40.dp)
                    .alpha(0.3f),
                color = OnSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 中部：迷你播放器
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceVariant.copy(alpha = 0.3f),
            tonalElevation = 0.dp,
            onClick = onPlayPause
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 播放/暂停图标
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 32.sp,
                        color = Primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 播放状态文字
                    Text(
                        text = if (isPlaying) "播放中" else "已暂停",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    // 进度信息
                    if (duration > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatProgress(currentPosition, duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 底部：额外信息
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            // 状态指示灯
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "status-pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "status-alpha"
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Primary)
                        .alpha(alpha)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "运行中",
                    style = MaterialTheme.typography.bodySmall,
                    color = Success,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 开发者信息
            Text(
                text = "© 2024",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * 格式化进度显示
 */
private fun formatProgress(position: Long, duration: Long): String {
    val currentMinutes = position / 60000
    val currentSeconds = (position % 60000) / 1000
    val totalMinutes = duration / 60000
    val totalSeconds = (duration % 60000) / 1000

    return String.format("%02d:%02d / %02d:%02d",
        currentMinutes, currentSeconds,
        totalMinutes, totalSeconds
    )
}

