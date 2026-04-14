package com.example.floatingscreencasting.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * 音频路由管理类
 * 负责将音频输出路由到蓝牙A2DP设备
 */
class AudioRouteManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // 蓝牙适配器
    private val bluetoothAdapter: BluetoothAdapter? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothManager.adapter
    } else null

    // 音频焦点请求
    private var audioFocusRequest: AudioFocusRequest? = null

    // 是否已连接A2DP设备
    var isA2dpConnected: Boolean = false
        private set

    // 蓝牙连接状态监听器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED)
                    val previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED)

                    if (state == BluetoothAdapter.STATE_CONNECTED || state == BluetoothAdapter.STATE_DISCONNECTED) {
                        Log.d("AudioRouteManager", "蓝牙连接状态变化: $previousState -> $state")
                        checkA2dpConnection()
                    }
                }
                android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(android.bluetooth.BluetoothA2dp.EXTRA_STATE, android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED)
                    Log.d("AudioRouteManager", "A2DP连接状态变化: $state")
                    checkA2dpConnection()
                }
            }
        }
    }

    init {
        registerReceiver()
        checkA2dpConnection()
    }

    /**
     * 注册蓝牙状态监听
     */
    private fun registerReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
            Log.d("AudioRouteManager", "蓝牙状态监听已注册")
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "注册蓝牙监听失败", e)
        }
    }

    /**
     * 注销监听
     */
    fun release() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
            abandonAudioFocus()
            Log.d("AudioRouteManager", "AudioRouteManager已释放")
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "注销监听失败", e)
        }
    }

    /**
     * 检查A2DP连接状态
     */
    private fun checkA2dpConnection() {
        isA2dpConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bluetoothAdapter != null) {
            val state = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP)
            state == BluetoothProfile.STATE_CONNECTED
        } else {
            false
        }
        Log.d("AudioRouteManager", "A2DP连接状态: $isA2dpConnected")
    }

    /**
     * 请求音频焦点并设置音频属性
     */
    fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 使用 AudioFocusRequest
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d("AudioRouteManager", "音频焦点变化: $focusChange")
                    }
                    .build()

                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                // Android 8.0 以下使用旧API
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange ->
                        Log.d("AudioRouteManager", "音频焦点变化: $focusChange")
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "请求音频焦点失败", e)
            false
        }
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
                audioFocusRequest = null
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { focusChange ->
                    Log.d("AudioRouteManager", "音频焦点放弃: $focusChange")
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "放弃音频焦点失败", e)
        }
    }

    /**
     * 设置音频输出到蓝牙设备
     * 这个方法在播放前调用，确保音频路由到蓝牙
     */
    fun setAudioRouteToBluetooth() {
        Log.d("AudioRouteManager", "========== 设置音频路由到蓝牙 ==========")
        Log.d("AudioRouteManager", "A2DP已连接: $isA2dpConnected")

        if (!isA2dpConnected) {
            Log.w("AudioRouteManager", "A2DP设备未连接，无法路由音频到蓝牙")
            return
        }

        try {
            // 获取可用的音频设备并打印信息
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val bluetoothA2dpDevices = devices.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
                Log.d("AudioRouteManager", "检测到 ${bluetoothA2dpDevices.size} 个蓝牙A2DP设备")
                bluetoothA2dpDevices.forEach { device ->
                    Log.d("AudioRouteManager", "  蓝牙设备ID: ${device.id}")
                }
            }

            // 重要：不做任何AudioManager设置！
            // 让ExoPlayer和Android系统自动处理音频路由
            // ExoPlayer会根据AudioAttributes自动选择合适的输出设备
            // 当USAGE_MEDIA且A2DP设备连接时，系统会自动路由到蓝牙

            Log.d("AudioRouteManager", "音频路由配置完成 - 依赖ExoPlayer和系统自动路由")
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "设置音频路由失败", e)
        }
    }

    /**
     * 获取可用的音频设备列表
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getAvailableAudioDevices(): List<String> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val deviceNames = mutableListOf<String>()

        for (device in devices) {
            val typeName = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙A2DP"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙SCO"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB耳机"
                else -> "其他(${device.type})"
            }
            deviceNames.add(typeName)
            Log.d("AudioRouteManager", "检测到音频设备: $typeName (ID: ${device.id})")
        }

        return deviceNames
    }

    /**
     * 尝试使用API 31+的方法设置通信设备（如果可用）
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun setCommunicationDeviceToBluetooth(): Boolean {
        if (!isA2dpConnected) return false

        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val bluetoothDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }

            if (bluetoothDevice != null) {
                val success = audioManager.setCommunicationDevice(bluetoothDevice)
                Log.d("AudioRouteManager", "设置通信设备结果: $success")
                return success
            }
        } catch (e: Exception) {
            Log.e("AudioRouteManager", "设置通信设备失败", e)
        }

        return false
    }

    /**
     * 重置音频路由到默认（扬声器）
     */
    fun resetAudioRoute() {
        Log.d("AudioRouteManager", "重置音频路由到默认")
        // 不做任何操作，让系统自动恢复
    }
}
