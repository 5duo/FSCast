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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 现代化悬浮窗控制卡片
 */
@Composable
fun ModernCastingControlCard(
    isWindowVisible: Boolean,
    castingStatus: String,
    onToggleWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModernCard(modifier = modifier) {
        Column {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "悬浮窗控制",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )
                // 状态指示灯
                StatusIndicator(isWindowVisible)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 状态信息
            StatusCard(
                title = "投屏状态",
                status = castingStatus,
                isActive = isWindowVisible,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModernActionButton(
                    text = if (isWindowVisible) "关闭悬浮窗" else "打开悬浮窗",
                    icon = if (isWindowVisible) "✕" else "✓",
                    onClick = onToggleWindow,
                    modifier = Modifier.weight(1f),
                    isActive = isWindowVisible
                )
            }
        }
    }
}

/**
 * 状态指示器组件
 */
@Composable
private fun StatusIndicator(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "status-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status-scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status-alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(scale)
                .background(
                    color = if (isActive) Success else SurfaceVariant,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isActive) "运行中" else "已停止",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) Success else OnSurfaceVariant
        )
    }
}
