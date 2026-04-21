package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 快捷设置网格
 * 常用设置快速访问，可展开/折叠
 */
@Composable
fun QuickSettingsGrid(
    onRestartWebSocket: () -> Unit,
    onCenterWindow: () -> Unit,
    onMaximizeWindow: () -> Unit,
    onResetWindow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 标题和展开按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "快捷设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (isExpanded) "收起 ▼" else "展开 ▼",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary,
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 快捷设置网格
            if (isExpanded) {
                // 展开状态：显示所有快捷设置
                QuickSettingsGridExpanded(
                    onRestartWebSocket = onRestartWebSocket,
                    onCenterWindow = onCenterWindow,
                    onMaximizeWindow = onMaximizeWindow,
                    onResetWindow = onResetWindow,
                    onOpenSettings = onOpenSettings
                )
            } else {
                // 折叠状态：只显示最常用的2个
                QuickSettingsGridCollapsed(
                    onRestartWebSocket = onRestartWebSocket,
                    onCenterWindow = onCenterWindow
                )
            }
        }
    }
}

/**
 * 折叠状态：显示2个最常用的快捷设置
 */
@Composable
private fun QuickSettingsGridCollapsed(
    onRestartWebSocket: () -> Unit,
    onCenterWindow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickSettingItem(
            icon = "🔄",
            label = "重启服务",
            description = "WebSocket",
            onClick = onRestartWebSocket,
            modifier = Modifier.weight(1f)
        )

        QuickSettingItem(
            icon = "🎯",
            label = "窗口居中",
            description = "快速居中",
            onClick = onCenterWindow,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 展开状态：显示所有快捷设置
 */
@Composable
private fun QuickSettingsGridExpanded(
    onRestartWebSocket: () -> Unit,
    onCenterWindow: () -> Unit,
    onMaximizeWindow: () -> Unit,
    onResetWindow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 第一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickSettingItem(
                icon = "🔄",
                label = "重启服务",
                description = "WebSocket",
                onClick = onRestartWebSocket,
                modifier = Modifier.weight(1f)
            )

            QuickSettingItem(
                icon = "🎯",
                label = "窗口居中",
                description = "快速居中",
                onClick = onCenterWindow,
                modifier = Modifier.weight(1f)
            )

            QuickSettingItem(
                icon = "📐",
                label = "最大化",
                description = "全屏显示",
                onClick = onMaximizeWindow,
                modifier = Modifier.weight(1f)
            )
        }

        // 第二行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickSettingItem(
                icon = "🔄",
                label = "重置",
                description = "默认大小",
                onClick = onResetWindow,
                modifier = Modifier.weight(1f)
            )

            QuickSettingItem(
                icon = "⚙️",
                label = "设置",
                description = "更多选项",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 快捷设置项
 */
@Composable
private fun QuickSettingItem(
    icon: String,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(70.dp),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceVariant.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.displayLarge,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface,
                fontSize = 13.sp
            )
        }
    }
}
