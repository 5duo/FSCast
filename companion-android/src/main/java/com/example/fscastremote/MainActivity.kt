package com.example.fscastremote

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fscastremote.model.ConnectionInfo
import com.example.fscastremote.model.ConnectionState
import com.example.fscastremote.service.AudioPlaybackService
import com.example.fscastremote.ui.components.MainScreen
import com.example.fscastremote.ui.theme.FSCastRemoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var serviceBinder: AudioPlaybackService.LocalBinder? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as AudioPlaybackService.LocalBinder
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            isServiceBound = false
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // 权限已授予
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }

        Intent(this, AudioPlaybackService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }

        setContent {
            FSCastRemoteTheme {
                MainApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    @Composable
    fun MainApp() {
        val scope = rememberCoroutineScope()
        var serverIp by remember { mutableStateOf("") }
        val discoveredDevices = remember { mutableStateListOf<ConnectionInfo>() }
        var isDiscovering by remember { mutableStateOf(false) }

        val isConnected = serviceBinder?.getService()?.getIsConnected() ?: false
        val connectionState = serviceBinder?.getService()?.getConnectionState()
            ?.collectAsStateWithLifecycle(ConnectionState.DISCONNECTED)?.value
            ?: ConnectionState.DISCONNECTED

        val playbackState = serviceBinder?.getService()?.getPlaybackState()
            ?.collectAsStateWithLifecycle()?.value

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            containerColor = Color.Transparent
        ) { paddingValues ->
            MainScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                isConnected = isConnected,
                connectionState = connectionState,
                playbackState = playbackState,
                serverIp = serverIp,
                discoveredDevices = discoveredDevices.toList(),
                isDiscovering = isDiscovering,
                onConnect = { ip ->
                    scope.launch {
                        serviceBinder?.getService()?.connect(ip, 19880)
                        serverIp = ip
                    }
                },
                onDisconnect = {
                    serviceBinder?.getService()?.disconnect()
                    serverIp = ""
                },
                onPlay = {
                    scope.launch {
                        serviceBinder?.getService()?.getAudioStreamClient()?.sendPlay()
                    }
                },
                onPause = {
                    scope.launch {
                        serviceBinder?.getService()?.getAudioStreamClient()?.sendPause()
                    }
                },
                onStop = {
                    scope.launch {
                        serviceBinder?.getService()?.getAudioStreamClient()?.sendStop()
                    }
                },
                onSeek = { positionMs ->
                    scope.launch {
                        serviceBinder?.getService()?.getAudioStreamClient()?.sendSeek(positionMs)
                    }
                },
                onSetAudioOutput = { output ->
                    scope.launch {
                        serviceBinder?.getService()?.getAudioStreamClient()?.sendSetAudioOutput(output)
                    }
                },
                onDiscover = {
                    scope.launch {
                        isDiscovering = true
                        discoveredDevices.clear()
                        try {
                            val devices = serviceBinder?.getService()?.getDiscoveryClient()?.discover() ?: emptyList()
                            discoveredDevices.addAll(devices)
                            if (devices.isNotEmpty()) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "发现 ${devices.size} 台设备",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // 自动连接第一个设备
                                val firstDevice = devices.first()
                                serviceBinder?.getService()?.connect(firstDevice.ip, firstDevice.port)
                                serverIp = firstDevice.ip
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "未发现设备，请确保车机已启动",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                "发现失败: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isDiscovering = false
                        }
                    }
                }
            )
        }
    }
}
