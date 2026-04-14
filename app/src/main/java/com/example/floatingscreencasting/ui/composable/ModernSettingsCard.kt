package com.example.floatingscreencasting.ui.composable

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.theme.*

/**
 * 现代化设置卡片
 */
@Composable
fun ModernSettingsCard(
    aspectRatio: AspectRatio,
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowHeight: Int,
    windowAlpha: Float,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onPositionXChange: (Int) -> Unit,
    onPositionYChange: (Int) -> Unit,
    onSizeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onCenterClick: () -> Unit,
    onMaximizeClick: () -> Unit,
    onDefaultClick: () -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModernCard(modifier = modifier) {
        Column {
            // 标题
            Text(
                text = "窗口设置",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 快捷操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModernQuickAction(
                    icon = "🔄",
                    label = "默认",
                    onClick = onDefaultClick,
                    modifier = Modifier.weight(1f)
                )
                ModernQuickAction(
                    icon = "📐",
                    label = "最大化",
                    onClick = onMaximizeClick,
                    modifier = Modifier.weight(1f)
                )
                ModernQuickAction(
                    icon = "💾",
                    label = "自定义",
                    onClick = onCustomClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 比例选择
            Text(
                text = "屏幕比例",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AspectRatio.values().forEach { ratio ->
                    ModernRatioButton(
                        ratio = ratio,
                        selected = aspectRatio == ratio,
                        onClick = { onAspectRatioChange(ratio) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 大小滑块
            if (aspectRatio == AspectRatio.CUSTOM) {
                // 自定义比例：显示宽度和高度两个滑块
                ModernSliderRow(
                    icon = "↔️",
                    label = "窗口宽度",
                    value = windowWidth,
                    valueRange = 240..1920,
                    onValueChange = { onSizeChange(it) },
                    suffix = "px",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                ModernSliderRow(
                    icon = "↕️",
                    label = "窗口高度",
                    value = windowHeight,
                    valueRange = 135..1080,
                    onValueChange = { onHeightChange(it) },
                    suffix = "px",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // 预设比例：只显示宽度滑块
                ModernSliderRow(
                    icon = "📏",
                    label = "窗口大小",
                    value = windowWidth,
                    valueRange = 240..1920,
                    onValueChange = { onSizeChange(it) },
                    suffix = "px",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 位置 X
            ModernSliderRow(
                icon = "↔️",
                label = "水平位置",
                value = windowX,
                valueRange = 0..1920,
                onValueChange = { onPositionXChange(it) },
                suffix = "px",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 位置 Y
            ModernSliderRow(
                icon = "↕️",
                label = "垂直位置",
                value = windowY,
                valueRange = 0..720,
                onValueChange = { onPositionYChange(it) },
                suffix = "px",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 透明度
            ModernSliderRow(
                icon = "🔆",
                label = "透明度",
                value = (windowAlpha * 100).toInt(),
                valueRange = 10..100,
                onValueChange = { onAlphaChange(it / 100f) },
                suffix = "%",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 快捷操作按钮
 */
@Composable
private fun ModernQuickAction(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = Primary.copy(alpha = 0.15f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
        }
    }
}

/**
 * 比例按钮
 */
@Composable
private fun ModernRatioButton(
    ratio: AspectRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        Primary
    } else {
        SurfaceVariant.copy(alpha = 0.5f)
    }

    val textColor = if (selected) {
        OnPrimary
    } else {
        OnSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ratio.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
