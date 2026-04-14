package com.example.floatingscreencasting.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * 音频输出管理类
 * 负责检测蓝牙音频连接状态
 */
class AudioOutputHelper(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager: BluetoothManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    } else null
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    /**
     * 检查是否有A2DP设备连接
     * 注意：这个方法使用简化的检查逻辑
     */
    @SuppressLint("NewApi")
    fun hasConnectedA2dpDevice(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bluetoothAdapter != null) {
            try {
                // 检查是否有已绑定的A2DP设备
                val a2dpProfileConnected = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP)
                val isConnected = a2dpProfileConnected == BluetoothProfile.STATE_CONNECTED
                Log.d("AudioOutputHelper", "A2DP连接状态: $a2dpProfileConnected, 已连接: $isConnected")
                return isConnected
            } catch (e: Exception) {
                Log.e("AudioOutputHelper", "检查A2DP连接失败", e)
            }
        }
        return false
    }

    /**
     * 检查蓝牙是否启用
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * 获取当前音频输出描述
     */
    fun getCurrentAudioOutputDescription(): String {
        // 检查是否有蓝牙A2DP连接
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasConnectedA2dpDevice()) {
            return "蓝牙音频（已连接）"
        }

        // 检查蓝牙是否开启
        if (isBluetoothEnabled()) {
            return "蓝牙（未连接设备）"
        }

        // 默认返回系统扬声器
        return "系统扬声器"
    }

    /**
     * 打开系统蓝牙设置
     */
    fun openBluetoothSettings(): Boolean {
        return try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("AudioOutputHelper", "打开蓝牙设置失败", e)
            false
        }
    }
}
