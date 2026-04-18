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
import com.example.floatingscreencasting.ui.model.DisplayInfo

/**
 * 投屏状态卡片
 */
@Composable
fun CastingStatusCard(
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
            // 标题
            Text(
                text = "投屏状态",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 投屏状态显示
            StatusCard(
                title = "状态",
                status = castingStatus,
                isActive = isWindowVisible,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 屏幕选择
            if (availableDisplays.isNotEmpty()) {
                Text(
                    text = "投屏屏幕",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                var expanded by remember { mutableStateOf(false) }
                val selectedDisplay = availableDisplays.firstOrNull { it.id == selectedDisplayId }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceVariant.copy(alpha = 0.3f),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📱 ${selectedDisplay?.toString() ?: "未选择"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurface
                            )
                            Text(
                                text = "▼",
                                style = MaterialTheme.typography.bodySmall,
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
                                    Text(display.toString(), style = MaterialTheme.typography.bodyMedium)
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

            // 打开/关闭悬浮窗按钮
            ModernActionButton(
                text = if (isWindowVisible) "关闭悬浮窗" else "打开悬浮窗",
                icon = if (isWindowVisible) "✕" else "✓",
                onClick = onToggleWindow,
                modifier = Modifier.fillMaxWidth(),
                isActive = isWindowVisible
            )
        }
    }
}

/**
 * WebSocket状态卡片
 */
@Composable
fun WebSocketStatusCard(
    isWebSocketRunning: Boolean = true,
    connectedPhoneDevice: String? = null,
    phoneDeviceCount: Int = 0,
    onRestartWebSocket: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModernCard(modifier = modifier) {
        Column {
            // 标题
            Text(
                text = "WebSocket",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // WebSocket连接状态
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceVariant.copy(alpha = 0.3f),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isWebSocketRunning && phoneDeviceCount > 0) Success else SurfaceVariant,
                                        shape = CircleShape
                                    )
                                    .then(
                                        if (isWebSocketRunning && phoneDeviceCount > 0) {
                                            Modifier.scale(1.2f)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (phoneDeviceCount > 0) "已连接" else "未连接",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (phoneDeviceCount > 0) Success else OnSurfaceVariant
                            )
                        }
                        Text(
                            text = "$phoneDeviceCount 设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }

                    if (connectedPhoneDevice != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = connectedPhoneDevice,
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 重启WebSocket按钮
            ModernActionButton(
                text = "重启服务",
                icon = "🔌",
                onClick = onRestartWebSocket,
                modifier = Modifier.fillMaxWidth(),
                isActive = false
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 服务状态说明
            Text(
                text = "端口: 9999\n等待手机端连接",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.4f
            )
        }
    }
}

/**
 * 两个控制卡片的容器（左右排列）
 */
@Composable
fun ControlCardsRow(
    isWindowVisible: Boolean,
    castingStatus: String,
    selectedDisplayId: Int,
    availableDisplays: List<DisplayInfo>,
    onToggleWindow: () -> Unit,
    onDisplayChange: (Int) -> Unit,
    isWebSocketRunning: Boolean,
    connectedPhoneDevice: String?,
    phoneDeviceCount: Int,
    onRestartWebSocket: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CastingStatusCard(
            isWindowVisible = isWindowVisible,
            castingStatus = castingStatus,
            selectedDisplayId = selectedDisplayId,
            availableDisplays = availableDisplays,
            onToggleWindow = onToggleWindow,
            onDisplayChange = onDisplayChange,
            modifier = Modifier.weight(1f)
        )

        WebSocketStatusCard(
            isWebSocketRunning = isWebSocketRunning,
            connectedPhoneDevice = connectedPhoneDevice,
            phoneDeviceCount = phoneDeviceCount,
            onRestartWebSocket = onRestartWebSocket,
            modifier = Modifier.weight(1f)
        )
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
