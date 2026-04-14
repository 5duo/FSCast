package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 屏幕比例枚举
 */
enum class AspectRatio(val displayName: String, val width: Int, val height: Int) {
    RATIO_16_9("16:9", 16, 9),
    RATIO_4_3("4:3", 4, 3),
    RATIO_PORTRAIT("竖屏", 9, 16),
    CUSTOM("自定义", 0, 0)
}

/**
 * 设置卡片
 * 包含屏幕比例、位置、大小、透明度设置
 */
@Composable
fun SettingsCard(
    aspectRatio: AspectRatio,
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowAlpha: Float,
    displayWidth: Int = 1920,
    displayHeight: Int = 720,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onPositionXChange: (Int) -> Unit,
    onPositionYChange: (Int) -> Unit,
    onSizeChange: (Int) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onCenterClick: () -> Unit,
    onMaximizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IosCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 屏幕比例选择
            SettingSectionTitle(text = "屏幕比例")
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AspectRatio.values().forEach { ratio ->
                    IosSelectionButton(
                        onClick = { onAspectRatioChange(ratio) },
                        selected = aspectRatio == ratio,
                        text = ratio.displayName,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 位置控制
            SettingSectionTitle(text = "位置")
            Spacer(modifier = Modifier.height(12.dp))

            PositionSlider(
                label = "X",
                value = windowX,
                onValueChange = { onPositionXChange(it.toInt()) },
                valueRange = 0f..displayWidth.toFloat(),
                steps = (displayWidth / 10).toInt(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            PositionSlider(
                label = "Y",
                value = windowY,
                onValueChange = { onPositionYChange(it.toInt()) },
                valueRange = 0f..displayHeight.toFloat(),
                steps = (displayHeight / 10).toInt(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 大小控制
            SettingSectionTitle(text = "大小")
            Spacer(modifier = Modifier.height(12.dp))

            PositionSlider(
                label = "W",
                value = windowWidth,
                onValueChange = { onSizeChange(it.toInt()) },
                valueRange = 160f..displayWidth.toFloat(),
                steps = ((displayWidth - 160) / 10).toInt(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 透明度控制
            SettingSectionTitle(text = "透明度")
            Spacer(modifier = Modifier.height(12.dp))

            PositionSlider(
                label = "%",
                value = (windowAlpha * 100).toInt(),
                onValueChange = { onAlphaChange(it / 100f) },
                valueRange = 0f..100f,
                steps = 100,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 快捷操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IosFilledButton(
                    onClick = onCenterClick,
                    text = "居中",
                    modifier = Modifier
                        .height(44.dp)
                        .weight(1f)
                )

                IosFilledButton(
                    onClick = onMaximizeClick,
                    text = "最大化",
                    modifier = Modifier
                        .height(44.dp)
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * 设置区域标题
 */
@Composable
private fun SettingSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * 位置/大小滑块组件
 */
@Composable
private fun PositionSlider(
    label: String,
    value: Int,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1920f,
    steps: Int = 0,  // 0表示连续滑块
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标签
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )

        // 滑块
        IosSlider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f)
        )

        // 数值显示
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(44.dp)
                .padding(start = 8.dp)
        )
    }
}
