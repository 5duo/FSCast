package com.example.floatingscreencasting.dlna

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.floatingscreencasting.data.remote.dlna.DlnaControlPoint.DlnaDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手机设备管理器
 * 管理已发现的DLNA设备（主要是手机端FSCast Remote）
 */
class PhoneDeviceManager(context: Context) {

    companion object {
        private const val TAG = "PhoneDeviceManager"
        private const val PREFS_NAME = "phone_devices"
        private const val KEY_SELECTED_DEVICE_UUID = "selected_device_uuid"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 已发现的设备列表
    private val _discoveredDevices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DlnaDevice>> = _discoveredDevices.asStateFlow()

    // 当前选中的设备
    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()

    /**
     * 更新已发现的设备列表
     */
    fun updateDevices(devices: List<DlnaDevice>) {
        // 过滤出可能是手机的设备（通过设备名称判断）
        val phoneDevices = devices.filter { device ->
            device.friendlyName.contains("FSCast", ignoreCase = true) ||
            device.friendlyName.contains("Remote", ignoreCase = true) ||
            device.friendlyName.contains("Phone", ignoreCase = true) ||
            device.manufacturer.contains("HarmonyOS", ignoreCase = true) ||
            device.manufacturer.contains("Honor", ignoreCase = true)
        }

        _discoveredDevices.value = phoneDevices

        Log.d(TAG, "更新设备列表: 发现 ${phoneDevices.size} 个手机设备")

        // 如果当前没有选中设备，尝试恢复上次的选择
        if (_selectedDevice.value == null) {
            restoreSelectedDevice(phoneDevices)
        }
    }

    /**
     * 选择设备
     */
    fun selectDevice(device: DlnaDevice) {
        _selectedDevice.value = device

        // 保存选择
        prefs.edit().putString(KEY_SELECTED_DEVICE_UUID, device.uuid).apply()

        Log.i(TAG, "已选择设备: ${device.friendlyName} (${device.ipAddress})")
    }

    /**
     * 取消选择设备
     */
    fun deselectDevice() {
        _selectedDevice.value?.let {
            Log.i(TAG, "取消选择设备: ${it.friendlyName}")
        }
        _selectedDevice.value = null

        // 清除保存的选择
        prefs.edit().remove(KEY_SELECTED_DEVICE_UUID).apply()
    }

    /**
     * 获取当前选中的设备
     */
    fun getSelectedDevice(): DlnaDevice? {
        return _selectedDevice.value
    }

    /**
     * 检查是否有选中的设备
     */
    fun hasSelectedDevice(): Boolean {
        return _selectedDevice.value != null
    }

    /**
     * 恢复上次选择的设备
     */
    private fun restoreSelectedDevice(devices: List<DlnaDevice>) {
        val savedUuid = prefs.getString(KEY_SELECTED_DEVICE_UUID, null)
        if (savedUuid != null) {
            val device = devices.find { it.uuid == savedUuid }
            if (device != null) {
                _selectedDevice.value = device
                Log.i(TAG, "恢复上次选择的设备: ${device.friendlyName}")
            } else {
                // 保存的设备不在列表中，清除保存
                prefs.edit().remove(KEY_SELECTED_DEVICE_UUID).apply()
            }
        }
    }

    /**
     * 清除所有设备
     */
    fun clearDevices() {
        _discoveredDevices.value = emptyList()
        Log.d(TAG, "清除所有设备")
    }

    /**
     * 获取设备数量
     */
    fun getDeviceCount(): Int {
        return _discoveredDevices.value.size
    }

    /**
     * 手动添加已知IP的设备
     * 用于当SSDP发现不可用时，直接通过IP连接
     */
    fun addKnownDevice(ipAddress: String, port: Int = 49153) {
        val knownDevice = DlnaDevice(
            friendlyName = "FSCast Remote (Manual)",
            location = "http://$ipAddress:$port/description.xml",
            uuid = "manual-phone-$ipAddress",
            manufacturer = "HarmonyOS",
            controlUrl = "http://$ipAddress:$port/control",
            ipAddress = ipAddress
        )

        // 检查是否已存在
        val currentDevices = _discoveredDevices.value.toMutableList()
        val existingIndex = currentDevices.indexOfFirst { it.uuid == knownDevice.uuid }

        if (existingIndex >= 0) {
            currentDevices[existingIndex] = knownDevice
        } else {
            currentDevices.add(knownDevice)
        }

        _discoveredDevices.value = currentDevices
        Log.i(TAG, "已添加已知设备: $ipAddress:$port")

        // 自动选中这个设备（手动添加的设备优先级更高）
        selectDevice(knownDevice)
    }
}
