package com.example.floatingscreencasting.ui.composable

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.floatingscreencasting.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 播放信息区（优化版）
 * 采用清晰的视觉层级，减少视觉噪音
 */
@Composable
fun PlaybackInfoSection(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    castingStatus: String,
    videoTitle: String,
    videoUrl: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showQrCode by remember { mutableStateOf(false) }

    SectionCard(
        title = "播放信息",
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(28.dp)  // 增加元素间距到28dp
        ) {
            // 主要信息：投屏状态 + 视频标题
            PrimaryInfoRow(
                isPlaying = isPlaying,
                videoTitle = videoTitle
            )

            // 进度条区域
            if (duration > 0) {
                ProgressSection(
                    currentPosition = currentPosition,
                    duration = duration,
                    onSeek = onSeek
                )
            }

            // 次要操作：URL按钮
            if (videoUrl.isNotEmpty()) {
                UrlActionButton(
                    url = videoUrl,
                    onClick = { showQrCode = true }
                )
            }
        }
    }

    // 二维码弹窗
    if (showQrCode) {
        QrCodeDialog(
            url = videoUrl,
            onDismiss = { showQrCode = false }
        )
    }
}

/**
 * 主要信息行
 * 投屏状态指示器 + 视频标题
 */
@Composable
private fun PrimaryInfoRow(
    isPlaying: Boolean,
    videoTitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 播放状态指示器
        PlayStatusIndicator(
            isPlaying = isPlaying,
            modifier = Modifier.size(8.dp)
        )

        // 文本信息
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (videoTitle.isEmpty()) "等待投屏..." else videoTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = GoldOnSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (videoTitle.isNotEmpty()) {
                Text(
                    text = if (isPlaying) "正在投屏" else "已暂停",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldOnSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * 播放状态指示器
 */
@Composable
private fun PlayStatusIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )

    Box(
        modifier = modifier
            .background(
                if (isPlaying) Success.copy(alpha = alpha) else GoldSurfaceVariant,
                CircleShape
            )
    )
}

/**
 * 进度条区域
 */
@Composable
private fun ProgressSection(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

        // 进度条
        Slider(
            value = progress,
            onValueChange = { onSeek((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = GoldPrimary,
                activeTrackColor = GoldPrimary,
                inactiveTrackColor = GoldSurfaceVariant
            )
        )

        // 时间显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

/**
 * URL操作按钮
 */
@Composable
private fun UrlActionButton(
    url: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = GoldSurfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            MaterialIcon(
                imageVector = MaterialIconsRes.QR_CODE,
                contentDescription = "二维码",
                iconSize = 16.dp,
                tint = GoldPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "视频链接",
                style = MaterialTheme.typography.bodySmall,
                color = GoldPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "查看",
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 二维码弹窗
 */
@Composable
private fun QrCodeDialog(
    url: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = GoldSurface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Text(
                    text = "扫码访问视频",
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldOnSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                // 二维码图片
                QrCodeImage(
                    data = url,
                    size = 220.dp
                )

                // URL文本
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldOnSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // 关闭按钮
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary
                    )
                ) {
                    Text(
                        text = "关闭",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnGold,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * 二维码图片
 */
@Composable
private fun QrCodeImage(
    data: String,
    size: androidx.compose.ui.unit.Dp
) {
    androidx.compose.foundation.Image(
        bitmap = remember(data) {
            generateQrCode(data, size).asImageBitmap()
        },
        contentDescription = "二维码",
        modifier = Modifier.size(size)
    )
}

/**
 * 生成二维码
 */
private fun generateQrCode(data: String, size: androidx.compose.ui.unit.Dp): android.graphics.Bitmap {
    val qrCodeWriter = QRCodeWriter()
    val sizePx = size.value.toInt()
    val hints = mapOf<EncodeHintType, Any>(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb())
        }
    }
    return bitmap
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
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)  // 恢复标准padding
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GoldOnSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))  // 增加标题下方间距

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
