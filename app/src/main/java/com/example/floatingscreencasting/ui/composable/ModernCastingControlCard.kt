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
 * 屏幕信息数据类
 */
data class DisplayInfo(
    val id: Int,
    val name: String
) {
    override fun toString(): String = "$name (ID: $id)"
}

/**
 * 现代化悬浮窗控制卡片
 */
@Composable
fun ModernCastingControlCard(
    isWindowVisible: Boolean,
    castingStatus: String,
    selectedDisplayId: Int,
    availableDisplays: List<DisplayInfo>,
    onToggleWindow: () -> Unit,
    onDisplayChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ModernCard(modifier = modifier) {
        Column {
            // 状态信息
            StatusCard(
                title = "投屏状态",
                status = castingStatus,
                isActive = isWindowVisible,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 屏幕选择
            if (availableDisplays.isNotEmpty()) {
                Text(
                    text = "投屏屏幕",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                var expanded by remember { mutableStateOf(false) }
                val selectedDisplay = availableDisplays.firstOrNull { it.id == selectedDisplayId }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceVariant.copy(alpha = 0.3f),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📱 ${selectedDisplay?.toString() ?: "未选择"}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurface
                            )
                            Text(
                                text = "▼",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        availableDisplays.forEach { display ->
                            DropdownMenuItem(
                                text = {
                                    Text(display.toString())
                                },
                                onClick = {
                                    onDisplayChange(display.id)
                                    expanded = false
                                },
                                trailingIcon = if (display.id == selectedDisplayId) {
                                    { Text("✓", color = Primary) }
                                } else null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

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
