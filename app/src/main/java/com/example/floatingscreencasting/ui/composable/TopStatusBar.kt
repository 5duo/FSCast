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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 顶部状态栏
 * 整合所有核心状态信息，一目了然
 */
@Composable
fun TopStatusBar(
    castingStatus: String,
    isPlaying: Boolean,
    audioOutputMode: String,
    isPhoneConnected: Boolean,
    phoneDeviceCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 投屏状态
            StatusItem(
                label = "投屏",
                value = castingStatus,
                isActive = castingStatus.contains("播放中") ||
                          castingStatus.contains("已连接"),
                modifier = Modifier.weight(1f)
            )

            // 分隔线
            VerticalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .alpha(0.2f),
                color = OnSurfaceVariant
            )

            // 播放状态
            StatusItem(
                label = "播放",
                value = if (isPlaying) "播放中" else "已暂停",
                isActive = isPlaying,
                modifier = Modifier.weight(1f)
            )

            // 分隔线
            VerticalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .alpha(0.2f),
                color = OnSurfaceVariant
            )

            // 音频输出
            StatusItem(
                label = "音频",
                value = when (audioOutputMode) {
                    "speaker" -> "扬声器"
                    "phone" -> "手机"
                    else -> "系统"
                },
                isActive = true,
                modifier = Modifier.weight(1f)
            )

            // 分隔线
            VerticalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .alpha(0.2f),
                color = OnSurfaceVariant
            )

            // WebSocket连接
            StatusItem(
                label = "连接",
                value = if (isPhoneConnected) {
                    "$phoneDeviceCount 设备"
                } else {
                    "未连接"
                },
                isActive = isPhoneConnected && phoneDeviceCount > 0,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 状态项组件
 */
@Composable
private fun StatusItem(
    label: String,
    value: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status-pulse-$label")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status-alpha"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 状态指示灯
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) Success else SurfaceVariant
                    )
                    .then(
                        if (isActive) {
                            Modifier.alpha(alpha)
                        } else {
                            Modifier
                        }
                    )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 状态值
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) Success else OnSurfaceVariant,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )

        // 标签
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}
