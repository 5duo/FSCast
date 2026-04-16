package com.example.fscastremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column as ColumnLayout
import androidx.compose.foundation.layout.Spacer as SpacerLayout

@Composable
fun AudioOutputSelector(
    currentOutput: String,
    onOutputChange: (String) -> Unit
) {
    CardSurface {
        ColumnLayout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🔊 音频输出",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF1F5F9)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutputOption(
                    label = "手机/蓝牙",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    selected = currentOutput == "phone",
                    onClick = { onOutputChange("phone") },
                    modifier = Modifier.weight(1f)
                )

                OutputOption(
                    label = "车机扬声器",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    selected = currentOutput == "speaker",
                    onClick = { onOutputChange("speaker") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OutputOption(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF6366F1),
                Color(0xFF8B5CF6)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent
            )
        )
    }

    val borderColor = if (selected) {
        Color(0xFF6366F1)
    } else {
        Color(0xFF334155)
    }

    val textColor = if (selected) {
        Color.White
    } else {
        Color(0xFF94A3B8)
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )

        if (selected) {
            SpacerLayout(modifier = Modifier.width(4.dp))
            Text(
                text = "✓",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
