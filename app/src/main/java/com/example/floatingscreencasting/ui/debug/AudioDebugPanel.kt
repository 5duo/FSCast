package com.example.floatingscreencasting.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 音频调试参数
 * 所有参数都可以实时调整，立即生效
 */
class AudioDebugParams(
    bufferSizeMs: Int = 176,
    initialBufferMs: Int = 100,
    sleepStrategy: Int = 1,
    overflowStrategy: Int = 1,
    readTimeoutMs: Int = 10,
    audioTrackBufferMultiplier: Int = 2,
    enableDiagnosticLogs: Boolean = true
) {
    var bufferSizeMs by mutableStateOf(bufferSizeMs)
    var initialBufferMs by mutableStateOf(initialBufferMs)
    var sleepStrategy by mutableStateOf(sleepStrategy)
    var overflowStrategy by mutableStateOf(overflowStrategy)
    var readTimeoutMs by mutableStateOf(readTimeoutMs)
    var audioTrackBufferMultiplier by mutableStateOf(audioTrackBufferMultiplier)
    var enableDiagnosticLogs by mutableStateOf(enableDiagnosticLogs)

    val bufferSizeBytes: Int
        get() = (bufferSizeMs * 176.4).toInt()

    val initialBufferBytes: Int
        get() = (initialBufferMs * 176.4).toInt()

    val audioTrackBufferSize: Int
        get() = audioTrackBufferMultiplier * 28288
}

/**
 * 音频调试面板
 *
 * 点击测试按钮时弹出，包含所有可调节的参数
 */
@Composable
fun AudioDebugPanel(
    params: AudioDebugParams,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "音频调试参数",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // 参数控制区域
                ParameterSection("PcmRingBuffer缓冲区") {
                    SliderParam(
                        label = "缓冲区大小",
                        value = params.bufferSizeMs,
                        range = 50..2000,
                        unit = "ms",
                        onValueChange = { params.bufferSizeMs = it }
                    )

                    SliderParam(
                        label = "初始缓冲阈值",
                        value = params.initialBufferMs,
                        range = 0..1000,
                        unit = "ms",
                        onValueChange = { params.initialBufferMs = it }
                    )
                }

                ParameterSection("播放控制") {
                    EnumSliderParam(
                        label = "Sleep策略",
                        options = mapOf(
                            0 to "不sleep（尽可能快播放）",
                            1 to "按实际字节数sleep"
                        ),
                        selected = params.sleepStrategy,
                        onValueChange = { params.sleepStrategy = it }
                    )

                    EnumSliderParam(
                        label = "溢出策略",
                        options = mapOf(
                            0 to "覆盖旧数据",
                            1 to "丢弃新数据"
                        ),
                        selected = params.overflowStrategy,
                        onValueChange = { params.overflowStrategy = it }
                    )

                    SliderParam(
                        label = "读取超时",
                        value = params.readTimeoutMs,
                        range = 0..1000,
                        unit = "ms",
                        onValueChange = { params.readTimeoutMs = it }
                    )

                    SliderParam(
                        label = "AudioTrack缓冲倍数",
                        value = params.audioTrackBufferMultiplier,
                        range = 1..10,
                        unit = "x",
                        onValueChange = { params.audioTrackBufferMultiplier = it }
                    )
                }

                ParameterSection("其他") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "启用诊断日志",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = params.enableDiagnosticLogs,
                            onCheckedChange = { params.enableDiagnosticLogs = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 计算的值显示
                InfoSection("计算值") {
                    InfoItem("缓冲区大小", "${params.bufferSizeBytes} 字节 (${params.bufferSizeMs}ms)")
                    InfoItem("初始缓冲", "${params.initialBufferBytes} 字节 (${params.initialBufferMs}ms)")
                    InfoItem("AudioTrack缓冲", "${params.audioTrackBufferSize} 字节")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 应用按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = onApply,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("应用并重新测试")
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SliderParam(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    onValueChange: (Int) -> Unit
) {
    var sliderValue by remember(value) { mutableStateOf(value.toFloat()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "$value $unit",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
    }

    Slider(
        value = sliderValue,
        onValueChange = {
            sliderValue = it
            onValueChange(it.toInt())
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EnumSliderParam(
    label: String,
    options: Map<Int, String>,
    selected: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        var expanded by remember { mutableStateOf(false) }
        Box {
            Text(
                options[selected] ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { expanded = true }
                    .width(200.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(250.dp)
            ) {
                options.forEach { (key, value) ->
                    DropdownMenuItem(
                        text = { Text(value) },
                        onClick = {
                            onValueChange(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        content()
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
    }
}
