package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 状态概览卡片
 * 2x3网格显示核心状态信息和服务控制按钮
 */
@Composable
fun StatusOverviewCard(
    isPlaying: Boolean,
    currentVideoTitle: String,  // 新增参数：用于区分"未投屏"和"已暂停"状态
    isWindowVisible: Boolean,
    audioOutputMode: String,
    webSocketClientCount: Int,  // WebSocket连接的手机数量
    isWebSocketServerRunning: Boolean,  // WebSocket服务器是否运行
    onRestartWebSocket: () -> Unit,
    onScanDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 对话框状态
    var showRestartDialog by remember { mutableStateOf(false) }

    SectionCard(
        title = "状态概览",
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusUnit(
                    imageVector = MaterialIconsRes.DISPLAY,
                    title = "投屏状态",
                    status = if (isWindowVisible) "已显示" else "已隐藏",
                    isActive = isWindowVisible,
                    modifier = Modifier.weight(1f)
                )

                StatusUnit(
                    imageVector = when {
                        currentVideoTitle.isEmpty() -> MaterialIconsRes.PAUSE
                        isPlaying -> MaterialIconsRes.PLAY
                        else -> MaterialIconsRes.PAUSE
                    },
                    title = "播放状态",
                    status = when {
                        currentVideoTitle.isEmpty() -> "未投屏"
                        isPlaying -> "播放中"
                        else -> "已暂停"
                    },
                    isActive = currentVideoTitle.isNotEmpty() && isPlaying,
                    modifier = Modifier.weight(1f)
                )

                // WebSocket服务器状态（只显示运行状态，点击可重启）
                StatusUnit(
                    imageVector = MaterialIconsRes.CONNECTION,
                    title = "WebSocket服务",
                    status = if (isWebSocketServerRunning) "运行中" else "已停止",
                    isActive = isWebSocketServerRunning,
                    onClick = { showRestartDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }

            // 第二行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusUnit(
                    imageVector = MaterialIconsRes.VOLUME_UP,
                    title = "音频输出",
                    status = if (audioOutputMode == "phone") "手机端" else "扬声器",
                    isActive = true,
                    modifier = Modifier.weight(1f)
                )

                StatusUnit(
                    imageVector = MaterialIconsRes.CONNECTION,
                    title = "连接状态",
                    status = if (webSocketClientCount > 0) "$webSocketClientCount 设备" else "未连接",
                    isActive = webSocketClientCount > 0,
                    modifier = Modifier.weight(1f)
                )

                ServiceControlUnit(
                    imageVector = MaterialIconsRes.SCAN,
                    title = "扫描设备",
                    status = "手机设备",
                    onClick = onScanDevices,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 重启WebSocket服务确认对话框
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = {
                Text(
                    text = "重启WebSocket服务",
                    color = GoldOnSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "确定要重启WebSocket服务器吗？",
                    color = GoldOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        onRestartWebSocket()
                    }
                ) {
                    Text("确定", color = GoldPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialog = false }
                ) {
                    Text("取消", color = GoldOnSurfaceVariant)
                }
            },
            containerColor = GoldSurface
        )
    }
}

/**
 * 状态单元组件（使用Material Icons）
 */
@Composable
private fun StatusUnit(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null  // 新增可选点击参数
) {
    val backgroundModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Surface(
        modifier = backgroundModifier,
        shape = RoundedCornerShape(8.dp),
        color = GoldSurfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 图标和状态指示灯
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Material Icon
                MaterialIcon(
                    imageVector = imageVector,
                    contentDescription = title,
                    iconSize = MaterialIconSizes.STATUS_UNIT,
                    tint = if (isActive) MaterialIconTints.SUCCESS else MaterialIconTints.DISABLED
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isActive) Success else GoldSurfaceVariant,
                            CircleShape
                        )
                )
            }

            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurfaceVariant,
                fontSize = 12.sp
            )

            // 状态文字
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) Success else GoldOnSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 服务控制单元组件（可点击）
 */
@Composable
private fun ServiceControlUnit(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = GoldPrimary.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 图标
            MaterialIcon(
                imageVector = imageVector,
                contentDescription = title,
                iconSize = MaterialIconSizes.STATUS_UNIT,
                tint = GoldPrimary
            )

            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurface,
                fontSize = 12.sp
            )

            // 状态文字
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = GoldPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 分区卡片容器
 */
@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GoldSurface.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GoldOnSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}
