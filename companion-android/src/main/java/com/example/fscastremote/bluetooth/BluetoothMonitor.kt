package com.example.fscastremote.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "BluetoothMonitor"

class BluetoothMonitor(private val context: Context) {

    private var isStarted = false
    private val _isBluetoothAudioConnected = MutableStateFlow(false)
    val isBluetoothAudioConnected: StateFlow<Boolean> = _isBluetoothAudioConnected.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    var onBluetoothDisconnected: (() -> Unit)? = null
    var onBluetoothConnected: (() -> Unit)? = null

    private val a2dpProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                Log.i(TAG, "A2DP service connected")
                updateA2dpState(proxy as BluetoothA2dp)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                Log.i(TAG, "A2DP service disconnected")
                _isBluetoothAudioConnected.value = false
                _connectedDevice.value = null
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    handleAdapterStateChanged(state)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    handleA2dpConnectionStateChanged(state, device)
                }
            }
        }
    }

    fun start() {
        if (isStarted) return
        isStarted = true

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothAdapter.getProfileProxy(context, a2dpProfileListener, BluetoothProfile.A2DP)

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)

        Log.i(TAG, "Bluetooth monitor started")
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false

        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Unregister receiver error: ${e.message}")
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, null)

        _isBluetoothAudioConnected.value = false
        _connectedDevice.value = null

        Log.i(TAG, "Bluetooth monitor stopped")
    }

    private fun handleAdapterStateChanged(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_OFF,
            BluetoothAdapter.STATE_TURNING_OFF -> {
                _isBluetoothAudioConnected.value = false
                _connectedDevice.value = null
            }
        }
    }

    private fun handleA2dpConnectionStateChanged(state: Int, device: BluetoothDevice?) {
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.i(TAG, "Bluetooth A2DP connected: ${device?.name}")
                _isBluetoothAudioConnected.value = true
                _connectedDevice.value = device
                onBluetoothConnected?.invoke()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.i(TAG, "Bluetooth A2DP disconnected: ${device?.name}")
                val wasConnected = _isBluetoothAudioConnected.value
                _isBluetoothAudioConnected.value = false
                _connectedDevice.value = null
                if (wasConnected) {
                    onBluetoothDisconnected?.invoke()
                }
            }
        }
    }

    private fun updateA2dpState(a2dp: BluetoothA2dp) {
        val connectedDevices = a2dp.connectedDevices
        if (connectedDevices.isNotEmpty()) {
            val device = connectedDevices.first()
            Log.i(TAG, "A2DP device already connected: ${device.name}")
            _isBluetoothAudioConnected.value = true
            _connectedDevice.value = device
        }
    }
}
