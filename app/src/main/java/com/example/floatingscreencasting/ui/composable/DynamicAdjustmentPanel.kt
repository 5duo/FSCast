package com.example.floatingscreencasting.ui.composable

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*
import com.example.floatingscreencasting.ui.AdjustmentPanelType

/**
 * 动态调整面板容器
 * 根据面板类型动态展开/收起对应的面板
 */
@Composable
fun DynamicAdjustmentPanel(
    panelType: AdjustmentPanelType?,
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = panelType != null,
        enter = expandVertically(
            animationSpec = tween(300, easing = EaseOutCubic),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(
            animationSpec = tween(300, easing = EaseInCubic),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        if (panelType != null) {
            AdjustmentPanelContent(
                panelType = panelType,
                aspectRatio = aspectRatio,
                windowX = windowX,
                windowY = windowY,
                windowWidth = windowWidth,
                windowHeight = windowHeight,
                windowAlpha = windowAlpha,
                onAspectRatioChange = onAspectRatioChange,
                onPositionXChange = onPositionXChange,
                onPositionYChange = onPositionYChange,
                onSizeChange = onSizeChange,
                onHeightChange = onHeightChange,
                onAlphaChange = onAlphaChange,
                onClose = onClose
            )
        }
    }
}

/**
 * 面板内容
 */
@Composable
private fun AdjustmentPanelContent(
    panelType: AdjustmentPanelType,
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
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = GoldSurface.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getPanelTitle(panelType),
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldOnSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = "✕",
                        color = GoldOnSurfaceVariant,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 面板具体内容
            when (panelType) {
                AdjustmentPanelType.POSITION -> {
                    PositionAdjustmentContent(
                        windowX = windowX,
                        windowY = windowY,
                        onPositionXChange = onPositionXChange,
                        onPositionYChange = onPositionYChange
                    )
                }
                AdjustmentPanelType.SIZE -> {
                    SizeAdjustmentContent(
                        aspectRatio = aspectRatio,
                        windowWidth = windowWidth,
                        windowHeight = windowHeight,
                        windowX = windowX,
                        windowY = windowY,
                        windowAlpha = windowAlpha,
                        onAspectRatioChange = onAspectRatioChange,
                        onSizeChange = onSizeChange,
                        onHeightChange = onHeightChange,
                        onPositionXChange = onPositionXChange,
                        onPositionYChange = onPositionYChange,
                        onAlphaChange = onAlphaChange
                    )
                }
                AdjustmentPanelType.TRANSPARENCY -> {
                    TransparencyAdjustmentContent(
                        windowAlpha = windowAlpha,
                        onAlphaChange = onAlphaChange
                    )
                }
                AdjustmentPanelType.ASPECT_RATIO -> {
                    AspectRatioAdjustmentContent(
                        aspectRatio = aspectRatio,
                        onAspectRatioChange = onAspectRatioChange
                    )
                }
            }
        }
    }
}

/**
 * 位置调整内容
 */
@Composable
private fun PositionAdjustmentContent(
    windowX: Int,
    windowY: Int,
    onPositionXChange: (Int) -> Unit,
    onPositionYChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ModernSliderRow(
            imageVector = MaterialIconsRes.ALIGN_HORIZONTAL,
            label = "水平位置",
            value = windowX,
            valueRange = 0..1920,
            onValueChange = onPositionXChange,
            suffix = "px",
            modifier = Modifier.fillMaxWidth()
        )

        ModernSliderRow(
            imageVector = MaterialIconsRes.ALIGN_VERTICAL,
            label = "垂直位置",
            value = windowY,
            valueRange = 0..720,
            onValueChange = onPositionYChange,
            suffix = "px",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 大小调整内容（含比例逻辑）
 */
@Composable
private fun SizeAdjustmentContent(
    aspectRatio: AspectRatio,
    windowWidth: Int,
    windowHeight: Int,
    windowX: Int,
    windowY: Int,
    windowAlpha: Float,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onSizeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onPositionXChange: (Int) -> Unit,
    onPositionYChange: (Int) -> Unit,
    onAlphaChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 比例选择
        Text(
            text = "屏幕比例",
            style = MaterialTheme.typography.bodySmall,
            color = GoldOnSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatio.values().forEach { ratio ->
                AspectRatioButton(
                    ratio = ratio,
                    selected = aspectRatio == ratio,
                    onClick = { onAspectRatioChange(ratio) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 根据比例模式显示不同滑块
        if (aspectRatio == AspectRatio.CUSTOM) {
            // 自定义比例：显示宽度+高度
            ModernSliderRow(
                imageVector = MaterialIconsRes.RESIZE,
                label = "窗口宽度",
                value = windowWidth,
                valueRange = 240..1920,
                onValueChange = onSizeChange,
                suffix = "px",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModernSliderRow(
                imageVector = MaterialIconsRes.RESIZE,
                label = "窗口高度",
                value = windowHeight,
                valueRange = 135..1080,
                onValueChange = onHeightChange,
                suffix = "px",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // 预设比例：只显示宽度，高度自动计算
            ModernSliderRow(
                imageVector = MaterialIconsRes.RESIZE,
                label = "窗口大小",
                value = windowWidth,
                valueRange = 240..1920,
                onValueChange = onSizeChange,
                suffix = "px",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 显示自动计算的高度信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "高度自动计算: ${windowHeight}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldOnSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 位置调整
        ModernSliderRow(
            imageVector = MaterialIconsRes.ALIGN_HORIZONTAL,
            label = "水平位置",
            value = windowX,
            valueRange = 0..1920,
            onValueChange = onPositionXChange,
            suffix = "px",
            modifier = Modifier.fillMaxWidth()
        )

        ModernSliderRow(
            imageVector = MaterialIconsRes.ALIGN_VERTICAL,
            label = "垂直位置",
            value = windowY,
            valueRange = 0..720,
            onValueChange = onPositionYChange,
            suffix = "px",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 透明度调整
        ModernSliderRow(
            imageVector = MaterialIconsRes.VISIBILITY,
            label = "透明度",
            value = (windowAlpha * 100).toInt(),
            valueRange = 10..100,
            onValueChange = { onAlphaChange(it / 100f) },
            suffix = "%",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 透明度调整内容
 */
@Composable
private fun TransparencyAdjustmentContent(
    windowAlpha: Float,
    onAlphaChange: (Float) -> Unit
) {
    ModernSliderRow(
        imageVector = MaterialIconsRes.VISIBILITY,
        label = "透明度",
        value = (windowAlpha * 100).toInt(),
        valueRange = 10..100,
        onValueChange = { onAlphaChange(it / 100f) },
        suffix = "%",
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 比例选择内容
 */
@Composable
private fun AspectRatioAdjustmentContent(
    aspectRatio: AspectRatio,
    onAspectRatioChange: (AspectRatio) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "选择屏幕比例",
            style = MaterialTheme.typography.bodyMedium,
            color = GoldOnSurface,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AspectRatio.values().forEach { ratio ->
                AspectRatioButton(
                    ratio = ratio,
                    selected = aspectRatio == ratio,
                    onClick = { onAspectRatioChange(ratio) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 比例说明
        RatioDescriptionCard(selectedRatio = aspectRatio)
    }
}

/**
 * 比例说明卡片
 */
@Composable
private fun RatioDescriptionCard(selectedRatio: AspectRatio) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = GoldSurfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (selectedRatio) {
                    AspectRatio.RATIO_16_9 -> "16:9 宽屏模式"
                    AspectRatio.RATIO_4_3 -> "4:3 标准模式"
                    AspectRatio.RATIO_PORTRAIT -> "9:16 竖屏模式"
                    AspectRatio.CUSTOM -> "自定义比例"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = GoldPrimary,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = when (selectedRatio) {
                    AspectRatio.RATIO_16_9 -> "适合观看宽屏视频内容"
                    AspectRatio.RATIO_4_3 -> "适合观看传统比例视频"
                    AspectRatio.RATIO_PORTRAIT -> "适合观看竖屏短视频"
                    AspectRatio.CUSTOM -> "可自由调整窗口宽高"
                },
                style = MaterialTheme.typography.bodySmall,
                color = GoldOnSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 获取面板标题
 */
private fun getPanelTitle(panelType: AdjustmentPanelType): String {
    return when (panelType) {
        AdjustmentPanelType.POSITION -> "位置调整"
        AdjustmentPanelType.SIZE -> "大小调整"
        AdjustmentPanelType.TRANSPARENCY -> "透明度调整"
        AdjustmentPanelType.ASPECT_RATIO -> "比例选择"
    }
}

/**
 * 比例按钮组件
 */
@Composable
private fun AspectRatioButton(
    ratio: AspectRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        GoldPrimary
    } else {
        GoldSurfaceVariant
    }

    val textColor = if (selected) {
        OnGold
    } else {
        GoldOnSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ratio.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}
