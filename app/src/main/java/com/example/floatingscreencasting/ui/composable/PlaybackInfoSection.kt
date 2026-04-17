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
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 播放信息区
 * 显示播放进度条和状态信息
 */
@Composable
fun PlaybackInfoSection(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    castingStatus: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "播放信息",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 投屏状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "投屏状态",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldOnSurfaceVariant,
                    fontSize = 14.sp
                )

                Surface(
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = if (castingStatus == "播放中") Success.copy(alpha = 0.8f) else GoldSurfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = castingStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (castingStatus == "播放中") Color.White else GoldOnSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            // 进度条
            val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = formatDuration(currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldOnSurfaceVariant,
                    fontSize = 12.sp
                )

                Slider(
                    value = progress,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary,
                        inactiveTrackColor = GoldSurfaceVariant
                    )
                )

                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldOnSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // 播放控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 播放/暂停
                ControlIconButtonSmall(
                    onClick = { /* TODO: 添加播放/暂停回调 */ },
                    imageVector = if (isPlaying) MaterialIconsRes.PAUSE else MaterialIconsRes.PLAY,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }
        }
    }
}

/**
 * 小型控制图标按钮
 */
@Composable
private fun ControlIconButtonSmall(
    onClick: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = GoldPrimary.copy(alpha = 0.8f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            MaterialIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                iconSize = 20.dp,
                tint = OnGold
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

/**
 * 格式化时间显示
 */
private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}
