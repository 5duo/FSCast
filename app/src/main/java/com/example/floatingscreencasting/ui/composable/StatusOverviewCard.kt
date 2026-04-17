package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    isWindowVisible: Boolean,
    audioOutputMode: String,
    phoneDeviceCount: Int,
    onRestartWebSocket: () -> Unit,
    onScanDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    imageVector = if (isPlaying) MaterialIconsRes.PLAY else MaterialIconsRes.PAUSE,
                    title = "播放状态",
                    status = if (isPlaying) "播放中" else "已暂停",
                    isActive = isPlaying,
                    modifier = Modifier.weight(1f)
                )

                ServiceControlUnit(
                    imageVector = MaterialIconsRes.REFRESH,
                    title = "重启服务",
                    status = "WebSocket",
                    onClick = onRestartWebSocket,
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
                    status = if (phoneDeviceCount > 0) "$phoneDeviceCount 设备" else "未连接",
                    isActive = phoneDeviceCount > 0,
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
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
