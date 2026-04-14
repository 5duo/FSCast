package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.theme.OnSurface
import com.example.floatingscreencasting.ui.theme.OnSurfaceVariant
import com.example.floatingscreencasting.ui.theme.Primary

/**
 * 继续观看卡片
 */
@Composable
fun ContinueWatchingCard(
    hasContinueWatching: Boolean,
    title: String,
    progress: Int,
    onContinueWatching: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasContinueWatching) {
        return
    }

    ModernCard(modifier = modifier.clickable { onContinueWatching() }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "继续观看",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 进度条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            color = Color(0xFFE0E0E0),
                            shape = RoundedCornerShape(2.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress / 100f)
                            .height(4.dp)
                            .background(
                                color = Primary,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "已观看 $progress%",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }

            // 右侧播放按钮
            Spacer(modifier = Modifier.width(16.dp))

            Surface(
                onClick = { onContinueWatching() },
                shape = RoundedCornerShape(12.dp),
                color = Primary.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
