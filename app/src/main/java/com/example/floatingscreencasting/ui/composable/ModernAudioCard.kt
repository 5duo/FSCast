package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 现代化音频输出卡片
 */
@Composable
fun ModernAudioCard(
    currentAudioOutput: String,
    onCheckBluetooth: () -> Unit,
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
                    text = "音频输出",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )
                AudioStatusIndicator()
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 当前输出设备
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "当前设备",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🔊",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = currentAudioOutput,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "系统默认输出",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 检查蓝牙按钮
            ModernActionButton(
                text = "检查蓝牙连接",
                icon = "🔵",
                onClick = onCheckBluetooth,
                modifier = Modifier.fillMaxWidth(),
                isActive = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 音频提示
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Info.copy(alpha = 0.1f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = "音频提示",
                            style = MaterialTheme.typography.labelSmall,
                            color = Info,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "投屏视频默认静音，可通过蓝牙设备输出音频",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 音频状态指示器
 */
@Composable
private fun AudioStatusIndicator() {
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            isAnimating = !isAnimating
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = Success.copy(
                        alpha = if (isAnimating) 1f else 0.3f
                    ),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "就绪",
            style = MaterialTheme.typography.bodySmall,
            color = Success
        )
    }
}
