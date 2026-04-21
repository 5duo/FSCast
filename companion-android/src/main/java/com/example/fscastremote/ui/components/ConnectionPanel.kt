package com.example.fscastremote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fscastremote.model.ConnectionInfo
import com.example.fscastremote.model.ConnectionState

@Composable
fun ConnectionPanel(
    isConnected: Boolean,
    connectionState: ConnectionState,
    serverIp: String,
    discoveredDevices: List<ConnectionInfo> = emptyList(),
    isDiscovering: Boolean = false,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDiscover: () -> Unit
) {
    CardSurface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF10B981) else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = if (isConnected) "已连接" else "未连接",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Color(0xFF10B981) else Color(0xFFF1F5F9)
                    )
                }

                if (isConnected) {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                            contentColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("断开", fontSize = 14.sp)
                    }
                }
            }

            if (isConnected && serverIp.isNotEmpty()) {
                Text(
                    text = serverIp,
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            } else {
                when (connectionState) {
                    ConnectionState.CONNECTING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF6366F1),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "正在连接...",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    ConnectionState.DISCONNECTED -> {
                        var inputIp by remember { mutableStateOf("") }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputIp,
                                onValueChange = { inputIp = it },
                                label = { Text("车机 IP 地址", fontSize = 12.sp) },
                                placeholder = { Text("192.168.1.100", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color(0xFF6366F1),
                                    unfocusedIndicatorColor = Color(0xFF334155),
                                    cursorColor = Color(0xFF6366F1)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { if (inputIp.isNotEmpty()) onConnect(inputIp) },
                                    enabled = inputIp.isNotEmpty() && !isDiscovering,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6366F1),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Text("连接", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = onDiscover,
                                    enabled = !isDiscovering,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDiscovering) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFF334155),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    if (isDiscovering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "发现设备",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // 显示发现的设备列表
                            if (discoveredDevices.isNotEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "发现的设备:",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    discoveredDevices.forEach { device ->
                                        Button(
                                            onClick = { onConnect(device.ip) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF1E293B),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.Start
                                            ) {
                                                Text(
                                                    text = device.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFF1F5F9)
                                                )
                                                Text(
                                                    text = "${device.ip}:${device.port}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ConnectionState.CONNECTED -> {
                        // Not reachable
                    }
                }
            }
        }
    }
}
