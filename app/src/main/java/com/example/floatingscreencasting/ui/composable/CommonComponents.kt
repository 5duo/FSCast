package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.ios_blue

/**
 * iOS风格实心按钮
 */
@Composable
fun IosFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    fontWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Bold
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = ios_blue,
            contentColor = Color.White,
            disabledContainerColor = ios_blue.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight
        )
    }
}

/**
 * iOS风格轮廓按钮
 */
@Composable
fun IosOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ios_blue
        ),
        border = BorderStroke(1.dp, ios_blue),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize
        )
    }
}

/**
 * iOS风格图标按钮
 */
@Composable
fun IosIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector,
    iconSize: Dp = 22.dp,
    contentDescription: String?,
    tint: Color = ios_blue
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(if (iconSize > 50.dp) iconSize else 50.dp),
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * iOS风格选择按钮（用于比例选择等）
 */
@Composable
fun IosSelectionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) ios_blue else Color.Transparent,
            contentColor = if (selected) Color.White else ios_blue
        ),
        border = BorderStroke(1.dp, ios_blue),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * iOS风格卡片
 */
@Composable
fun IosCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(content = content)
    }
}

/**
 * iOS风格滑块
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        colors = SliderDefaults.colors(
            activeTrackColor = ios_blue,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            thumbColor = ios_blue,
            disabledActiveTrackColor = ios_blue.copy(alpha = 0.3f),
            disabledInactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            disabledThumbColor = ios_blue.copy(alpha = 0.3f)
        )
    )
}
