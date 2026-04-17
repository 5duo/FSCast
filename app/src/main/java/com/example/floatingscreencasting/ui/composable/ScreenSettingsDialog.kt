package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.floatingscreencasting.ui.theme.*

/**
 * 屏幕设置弹窗
 * 替代原来的DynamicAdjustmentPanel，使用Dialog形式展示
 */
@Composable
fun ScreenSettingsDialog(
    isVisible: Boolean,
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
    onDismiss: () -> Unit
) {
    if (isVisible) {
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
                    .fillMaxWidth(0.5f)
                    .height(600.dp),
                shape = RoundedCornerShape(16.dp),
                color = GoldSurface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "屏幕设置",
                            style = MaterialTheme.typography.titleLarge,
                            color = GoldOnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = GoldOnSurfaceVariant
                            )
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 24.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 可滚动内容区域
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 屏幕比例
                        SettingSection(title = "屏幕比例") {
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
                        }

                        // 大小调整
                        SettingSection(title = "窗口大小") {
                            if (aspectRatio == AspectRatio.CUSTOM) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ModernSliderRow(
                                        imageVector = MaterialIconsRes.RESIZE,
                                        label = "宽度",
                                        value = windowWidth,
                                        valueRange = 240..1920,
                                        onValueChange = onSizeChange,
                                        suffix = "px"
                                    )
                                    ModernSliderRow(
                                        imageVector = MaterialIconsRes.RESIZE,
                                        label = "高度",
                                        value = windowHeight,
                                        valueRange = 135..1080,
                                        onValueChange = onHeightChange,
                                        suffix = "px"
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ModernSliderRow(
                                        imageVector = MaterialIconsRes.RESIZE,
                                        label = "大小",
                                        value = windowWidth,
                                        valueRange = 240..1920,
                                        onValueChange = onSizeChange,
                                        suffix = "px"
                                    )
                                    Text(
                                        text = "高度自动计算: ${windowHeight}px",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoldOnSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // 位置调整
                        SettingSection(title = "窗口位置") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ModernSliderRow(
                                    imageVector = MaterialIconsRes.ALIGN_HORIZONTAL,
                                    label = "水平位置",
                                    value = windowX,
                                    valueRange = 0..1920,
                                    onValueChange = onPositionXChange,
                                    suffix = "px"
                                )
                                ModernSliderRow(
                                    imageVector = MaterialIconsRes.ALIGN_VERTICAL,
                                    label = "垂直位置",
                                    value = windowY,
                                    valueRange = 0..720,
                                    onValueChange = onPositionYChange,
                                    suffix = "px"
                                )
                            }
                        }

                        // 透明度调整
                        SettingSection(title = "透明度") {
                            ModernSliderRow(
                                imageVector = MaterialIconsRes.VISIBILITY,
                                label = "透明度",
                                value = (windowAlpha * 100).toInt(),
                                valueRange = 10..100,
                                onValueChange = { onAlphaChange(it / 100f) },
                                suffix = "%"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 设置分组标题
 */
@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = GoldOnSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        content()
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
        modifier = modifier.height(44.dp),
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
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        }
    }
}
