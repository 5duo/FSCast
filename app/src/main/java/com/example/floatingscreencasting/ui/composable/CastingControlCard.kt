package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 悬浮窗控制卡片
 * 显示投屏状态和悬浮窗显示/隐藏按钮
 */
@Composable
fun CastingControlCard(
    isWindowVisible: Boolean,
    castingStatus: String,
    onToggleWindow: () -> Unit,
    modifier: Modifier = Modifier
) {
    IosCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = "悬浮窗",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 状态指示器和文字
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示器
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (castingStatus == "等待投屏") {
                                Color(0xFF8E8E93) // 使用可见的灰色
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.width(10.dp))

                // 状态文字
                Text(
                    text = castingStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // 显示/隐藏按钮
                IosFilledButton(
                    onClick = onToggleWindow,
                    text = if (isWindowVisible) "隐藏" else "显示",
                    modifier = Modifier.height(36.dp)
                )
            }
        }
    }
}
