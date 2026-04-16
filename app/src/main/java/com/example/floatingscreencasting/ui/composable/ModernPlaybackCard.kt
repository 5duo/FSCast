package com.example.floatingscreencasting.ui.composable

import androidx.compose.animation.core.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*
import com.example.floatingscreencasting.ui.StreamClient

/**
 * 现代化播放控制卡片
 * 使用渐变背景和更好的视觉层次
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernPlaybackControlCard(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isMuted: Boolean,
    audioOutputMode: String = "speaker",
    connectedClients: List<StreamClient> = emptyList(),
    selectedClientId: String? = null,
    localAudioTest: Boolean = false,  // 本地音频测试模式
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onToggleLocalAudio: () -> Unit = {},  // 切换本地音频测试
    onSeek: (Float) -> Unit,
    onSelectSpeaker: () -> Unit = {},
    onSelectDevice: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAudioOutputSheet by remember { mutableStateOf(false) }

    ModernCard(modifier = modifier) {
        Column {
            // 标题和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放控制",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 播放状态指示器
                    StatusIndicator(isPlaying = isPlaying, hasContent = duration > 0)

                    // 音频输出按钮
                    Surface(
                        onClick = { showAudioOutputSheet = true },
                        shape = CircleShape,
                        color = when {
                            audioOutputMode == "phone" && selectedClientId != null -> Success.copy(alpha = 0.2f)
                            connectedClients.isNotEmpty() -> Info.copy(alpha = 0.2f)
                            else -> SurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = when {
                                    audioOutputMode == "phone" && selectedClientId != null -> "📡✓"
                                    audioOutputMode == "phone" && selectedClientId == null -> "📡⚠"
                                    connectedClients.isNotEmpty() -> "📡"
                                    else -> "📡"
                                },
                                fontSize = 14.sp,
                                color = when {
                                    audioOutputMode == "phone" && selectedClientId != null -> Success
                                    connectedClients.isNotEmpty() -> Info
                                    else -> OnSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 进度条
            ModernProgressBar(
                currentPosition = currentPosition,
                duration = duration,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 播放控制按钮
            ModernPlaybackControls(
                isPlaying = isPlaying,
                isMuted = isMuted,
                localAudioTest = localAudioTest,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onPrevious = onPrevious,
                onNext = onNext,
                onMute = onMute,
                onToggleLocalAudio = onToggleLocalAudio
            )
        }
    }

    // 音频输出选择 BottomSheet
    if (showAudioOutputSheet) {
        AudioOutputBottomSheet(
            audioOutputMode = audioOutputMode,
            connectedClients = connectedClients,
            selectedClientId = selectedClientId,
            onSelectSpeaker = {
                onSelectSpeaker()
                showAudioOutputSheet = false
            },
            onSelectDevice = { clientId ->
                onSelectDevice(clientId)
                showAudioOutputSheet = false
            },
            onDismiss = { showAudioOutputSheet = false }
        )
    }
}

/**
 * 音频输出选择 BottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioOutputBottomSheet(
    audioOutputMode: String,
    connectedClients: List<StreamClient>,
    selectedClientId: String?,
    onSelectSpeaker: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        contentColor = OnSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题
            Text(
                text = "音频输出",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // 车机扬声器选项
            AudioOutputOption(
                icon = "🔈",
                title = "车机扬声器",
                subtitle = "音频从车机扬声器播放",
                isSelected = audioOutputMode == "speaker",
                onClick = onSelectSpeaker
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 已连接设备列表
            if (connectedClients.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(SurfaceVariant.copy(alpha = 0.3f))
                    )
                    Text(
                        text = "已连接设备 (${connectedClients.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(SurfaceVariant.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                connectedClients.forEach { client ->
                    AudioOutputOption(
                        icon = "📱",
                        title = client.name,
                        subtitle = "${client.address} (${client.platform})",
                        isSelected = selectedClientId == client.id,
                        onClick = { onSelectDevice(client.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // 无设备连接提示
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF334155).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "未发现手机设备",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请确保：\n• 手机已安装 FSCast Remote 应用\n• 手机和车机在同一 WiFi 网络下\n• 手机 App 已打开",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 音频输出选项卡片
 */
@Composable
private fun AudioOutputOption(
    icon: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        Primary.copy(alpha = 0.15f)
    } else {
        Color(0xFF334155).copy(alpha = 0.5f)
    }

    val borderColor = if (isSelected) {
        Primary.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            if (isSelected) {
                Text(
                    text = "✓",
                    fontSize = 20.sp,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 状态指示器
 */
@Composable
private fun StatusIndicator(isPlaying: Boolean, hasContent: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // 状态文字：根据是否有内容和播放状态决定
    val statusText = when {
        !hasContent -> "已停止"
        isPlaying -> "播放中"
        else -> "已暂停"
    }

    val isActive = hasContent && isPlaying

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(scale)
                .background(
                    color = if (isActive) Success else SurfaceVariant,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) Success else OnSurfaceVariant
        )
    }
}

/**
 * 现代化进度条
 */
@Composable
private fun ModernProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 时间显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "⏱",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                TimeLabel(currentPosition, "当前")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TimeLabel(duration, "总时长")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⏱",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 使用和设置滑块一样的现代化滑块
        ModernSlider(
            value = currentPosition.toFloat(),
            onValueChange = { onSeek(it) },
            valueRange = 0f..duration.toFloat(),
            steps = if (duration > 1000) 1000 else (duration - 1).toInt(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 时间标签
 */
@Composable
private fun TimeLabel(time: Long, label: String) {
    Text(
        text = formatTime(time),
        style = MaterialTheme.typography.titleMedium,
        color = OnSurface,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * 现代化播放控制按钮组
 */
@Composable
private fun ModernPlaybackControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    localAudioTest: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onToggleLocalAudio: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一集
        ModernControlButton(
            onClick = onPrevious,
            icon = "⏮",
            contentDescription = "上一集",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 播放/暂停（统一风格）
        ModernControlButton(
            onClick = onPlayPause,
            icon = if (isPlaying) "❚❚" else "▶",
            contentDescription = if (isPlaying) "暂停" else "播放",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 停止
        ModernControlButton(
            onClick = onStop,
            icon = "■",
            contentDescription = "停止",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 下一集
        ModernControlButton(
            onClick = onNext,
            icon = "⏭",
            contentDescription = "下一集",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 静音（统一风格）
        ModernControlButton(
            onClick = onMute,
            icon = if (isMuted) "🔇" else "🔊",
            contentDescription = if (isMuted) "取消静音" else "静音",
            size = 50.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 本地音频测试按钮
        ModernControlButton(
            onClick = onToggleLocalAudio,
            icon = "🔊🚗",
            contentDescription = "本地音频测试",
            size = 50.dp,
            tint = if (localAudioTest) Success else OnSurfaceVariant
        )
    }
}

/**
 * 标准控制按钮
 * 纯灰色背景
 */
@Composable
private fun ModernControlButton(
    onClick: () -> Unit,
    icon: String,
    contentDescription: String?,
    size: Dp,
    tint: Color = OnSurfaceVariant  // 添加tint参数，默认为灰色
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = SurfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 0.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = icon,
                fontSize = 24.sp,
                color = tint
            )
        }
    }
}

/**
 * 格式化时间
 */
private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
