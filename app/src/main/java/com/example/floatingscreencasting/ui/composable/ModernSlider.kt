package com.example.floatingscreencasting.ui.composable

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.theme.*
import kotlin.math.max
import kotlin.math.min

/**
 * Dp 转 Float 的扩展函数
 */
private fun Dp.toPxValue(): Float {
    // 简化转换，假设屏幕密度为 2
    return this.value * 2f
}

/**
 * 现代化滑块组件
 */
@Composable
fun ModernSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    showValue: Boolean = false,
    valueSuffix: String = "",
    icon: String? = null
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

    // 计算当前进度
    val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            if (icon != null) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // 滑块区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (enabled) {
                            SurfaceVariant.copy(alpha = 0.2f)
                        } else {
                            SurfaceVariant.copy(alpha = 0.1f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                ModernSliderTrack(
                    value = value,
                    valueRange = valueRange,
                    progress = progress,
                    isDragging = isDragging,
                    dragPosition = dragPosition,
                    onDragStart = {
                        if (enabled) isDragging = true
                    },
                    onDragChange = { newValue ->
                        if (enabled) {
                            // newValue 已经是计算好的值，直接使用
                            onValueChange(newValue)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        dragPosition = 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }

            // 值显示
            if (showValue) {
                Text(
                    text = "${value.toInt()}$valueSuffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.width(80.dp)
                )
            }
        }
    }
}

/**
 * 滑块轨道和滑块
 */
@Composable
private fun ModernSliderTrack(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    progress: Float,
    isDragging: Boolean,
    dragPosition: Float,
    onDragStart: () -> Unit,
    onDragChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbRadius = with(density) { 12.dp.toPx() }
    val trackHeight = with(density) { 6.dp.toPx() }

    var trackWidth by remember { mutableStateOf(0f) }

    // 根据位置计算值的函数（内联以访问trackWidth）
    fun calculateValueFromPosition(positionX: Float): Float {
        val effectiveWidth = trackWidth - thumbRadius * 2
        val rawProgress = (positionX - thumbRadius) / effectiveWidth
        val clampedProgress = rawProgress.coerceIn(0f, 1f)
        val range = valueRange.endInclusive - valueRange.start
        return valueRange.start + clampedProgress * range
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onDragStart()
                        // 点击时跳转到该位置
                        val clickValue = calculateValueFromPosition(offset.x)
                        onDragChange(clickValue)
                    },
                    onDragCancel = { onDragEnd() },
                    onDragEnd = { onDragEnd() },
                    onHorizontalDrag = { change, _ ->
                        // 使用绝对位置而不是增量
                        val newValue = calculateValueFromPosition(change.position.x)
                        onDragChange(newValue)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth()) {
            trackWidth = size.width

            val centerY = size.height / 2
            val effectiveWidth = trackWidth - thumbRadius * 2

            // 计算滑块位置
            val thumbPosition = thumbRadius + progress * effectiveWidth

            // 绘制背景轨道
            drawRoundRect(
                color = SurfaceVariant.copy(alpha = 0.3f),
                topLeft = Offset(thumbRadius, centerY - trackHeight / 2),
                size = androidx.compose.ui.geometry.Size(effectiveWidth, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
            )

            // 绘制已激活轨道（渐变）
            val gradientWidth = max(0f, (thumbPosition - thumbRadius))
            if (gradientWidth > 0) {
                drawRoundRect(
                    topLeft = Offset(thumbRadius, centerY - trackHeight / 2),
                    size = androidx.compose.ui.geometry.Size(gradientWidth, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
                    style = Stroke(width = trackHeight),
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Primary,
                            Secondary
                        )
                    )
                )
            }

            // 绘制滑块阴影
            val shadowOffset = if (isDragging) 8.dp.toPx() else 4.dp.toPx()
            drawCircle(
                color = GlowColor.copy(alpha = 0.3f),
                radius = thumbRadius + shadowOffset,
                center = Offset(
                    if (isDragging) thumbRadius + dragPosition else thumbPosition,
                    centerY
                )
            )

            // 绘制滑块外圈
            drawCircle(
                color = if (isDragging) Primary.copy(alpha = 0.3f) else Color.Transparent,
                radius = thumbRadius * 1.3f,
                center = Offset(
                    if (isDragging) thumbRadius + dragPosition else thumbPosition,
                    centerY
                )
            )

            // 绘制滑块
            drawCircle(
                color = if (isDragging) Primary else Color.White,
                radius = thumbRadius,
                center = Offset(
                    if (isDragging) thumbRadius + dragPosition else thumbPosition,
                    centerY
                )
            )

            // 绘制滑块内圈
            drawCircle(
                color = if (isDragging) Color.White else Primary,
                radius = thumbRadius * 0.5f,
                center = Offset(
                    if (isDragging) thumbRadius + dragPosition else thumbPosition,
                    centerY
                )
            )
        }
    }
}

/**
 * 改进的滑块行组件
 */
@Composable
fun ModernSliderRow(
    icon: String,
    label: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    suffix: String = "",
    isPercentage: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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
                    text = icon,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface
                )
            }
            Text(
                text = "$value$suffix",
                style = MaterialTheme.typography.bodyMedium,
                color = Primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        ModernSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = if (valueRange.last - valueRange.first <= 100) {
                valueRange.last - valueRange.first
            } else {
                0
            },
            modifier = Modifier.fillMaxWidth(),
            showValue = false
        )
    }
}
