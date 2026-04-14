package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 现代化卡片组件
 * 使用渐变背景和阴影效果
 */
@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = modifier
            .shadow(elevation, cardShape, spotColor = GlowColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            CardGradientStart,
                            CardGradientEnd
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = GlassBorder,
                    shape = cardShape
                )
                .padding(16.dp)
        ) {
            Column(content = content)
        }
    }
}

/**
 * 玻璃态卡片
 * 使用半透明背景和模糊效果
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)

    Surface(
        modifier = modifier
            .shadow(8.dp, cardShape, spotColor = GlowColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            ),
        shape = cardShape,
        color = GlassBackground,
        border = null,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .border(
                    width = 1.dp,
                    color = GlassBorder,
                    shape = cardShape
                )
                .padding(16.dp)
        ) {
            Column(content = content)
        }
    }
}

/**
 * 状态指示器卡片
 * 用于显示连接状态、播放状态等
 */
@Composable
fun StatusCard(
    title: String,
    status: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val statusColor = when {
        isActive -> Success
        status.contains("失败") -> Error
        status.contains("等待") -> Warning
        else -> OnSurfaceVariant
    }

    ModernCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }
            // 状态指示灯
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (isActive) Success else SurfaceVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * 带图标的卡片项
 */
@Composable
fun IconCardItem(
    icon: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = OnSurface
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge,
                color = Primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}

/**
 * 现代化操作按钮（公共组件）
 */
@Composable
fun ModernActionButton(
    text: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean
) {
    val backgroundColor = if (isActive) {
        Error.copy(alpha = 0.2f)
    } else {
        Primary.copy(alpha = 0.2f)
    }

    val textColor = if (isActive) Error else Primary

    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
