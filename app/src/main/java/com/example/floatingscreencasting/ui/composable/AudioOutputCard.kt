package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 音频输出卡片
 * 显示当前音频输出设备和检查蓝牙按钮
 */
@Composable
fun AudioOutputCard(
    currentAudioOutput: String,
    onCheckBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    IosCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = "音频输出",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface // 使用主题颜色
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 当前音频输出和控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前音频输出
                Text(
                    text = currentAudioOutput,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // 使用主题颜色
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 检查蓝牙按钮
                IosOutlinedButton(
                    onClick = onCheckBluetooth,
                    text = "检查蓝牙",
                    modifier = Modifier.height(40.dp)
                )
            }

            // 蓝牙提示
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击检查蓝牙连接状态，已连接时投屏声音会自动输出到蓝牙设备",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93) // 使用更可见的灰色
            )
        }
    }
}
