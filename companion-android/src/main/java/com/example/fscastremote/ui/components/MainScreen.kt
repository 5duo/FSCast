package com.example.fscastremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.fscastremote.model.ConnectionState
import com.example.fscastremote.model.PlaybackState

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    connectionState: ConnectionState,
    playbackState: PlaybackState?,
    serverIp: String,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onSetAudioOutput: (String) -> Unit,
    onDiscover: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopBar()

        ConnectionPanel(
            isConnected = isConnected,
            connectionState = connectionState,
            serverIp = serverIp,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onDiscover = onDiscover
        )

        if (isConnected) {
            PlayerControls(
                playbackState = playbackState,
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
                onSeek = onSeek
            )

            AudioOutputSelector(
                currentOutput = playbackState?.audioOutput ?: "speaker",
                onOutputChange = onSetAudioOutput
            )

            BluetoothStatusCard()
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "FSCast Remote",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun BluetoothStatusCard() {
    CardSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(24.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "🎧 蓝牙音频",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFF1F5F9)
                )

                Text(
                    text = "未连接蓝牙设备",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF6366F1).copy(alpha = 0.1f),
            Color(0xFF8B5CF6).copy(alpha = 0.1f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(gradientBrush)
            .background(
                Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(2.dp)
    ) {
        content()
    }
}
