package com.example.floatingscreencasting.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * 手机设备发现服务
 * 车机端通过UDP广播发现局域网内的手机设备
 */
class PhoneDiscoveryServer(private val context: Context) {

    companion object {
        private const val TAG = "PhoneDiscoveryServer"
        private const val DISCOVERY_PORT = 19891  // 设备发现端口
        private const val DISCOVERY_MESSAGE = "FSCAST_PHONE_DISCOVER"
        private const val RESPONSE_MESSAGE = "FSCAST_PHONE_RESPONSE"
    }

    private var discoverySocket: DatagramSocket? = null
    private var isRunning = false
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val multicastLock: WifiManager.MulticastLock?
        get() = context.getSystemService(Context.WIFI_SERVICE)
            ?.let { (it as WifiManager).createMulticastLock("FSCast_DISCOVERY") }

    /**
     * 已发现的手机设备列表
     */
    data class PhoneDevice(
        val id: String,
        val name: String,
        val address: String,
        val platform: String,
        val lastSeen: Long = System.currentTimeMillis(),
        var selected: Boolean = false
    )

    private val discoveredDevices = mutableMapOf<String, PhoneDevice>()

    // 设备列表变化回调
    var onDeviceListChanged: ((List<PhoneDevice>) -> Unit)? = null

    /**
     * 启动设备发现服务
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "PhoneDiscoveryServer已经在运行")
            return
        }

        try {
            // 获取WiFi多播锁
            multicastLock?.acquire()

            // 创建UDP套接字
            discoverySocket = DatagramSocket(DISCOVERY_PORT)
            discoverySocket?.broadcast = true
            discoverySocket?.soTimeout = 5000  // 5秒超时

            isRunning = true

            Log.i(TAG, "PhoneDiscoveryServer启动成功，监听端口: $DISCOVERY_PORT")

            // 启动发现协程
            discoveryScope.launch {
                discoverDevices()
            }

            // 启动响应监听协程
            discoveryScope.launch {
                listenForResponses()
            }

            // 定期刷新设备列表
            discoveryScope.launch {
                while (isRunning) {
                    delay(10000)  // 每10秒刷新一次
                    discoverDevices()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "启动PhoneDiscoveryServer失败", e)
            isRunning = false
            multicastLock?.release()
        }
    }

    /**
     * 广播发现请求
     */
    private suspend fun discoverDevices() {
        if (!isRunning) return

        try {
            val message = DISCOVERY_MESSAGE.toByteArray()
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(message, message.size, broadcastAddress, DISCOVERY_PORT)

            discoverySocket?.send(packet)
            Log.d(TAG, "已广播设备发现请求")

        } catch (e: Exception) {
            Log.e(TAG, "广播发现请求失败", e)
        }
    }

    /**
     * 监听手机端响应
     */
    private suspend fun listenForResponses() {
        val buffer = ByteArray(1024)

        while (isRunning) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                discoverySocket?.receive(packet)

                val message = String(packet.data, 0, packet.length)
                val address = packet.address.hostAddress

                Log.d(TAG, "收到响应: $message from $address")

                if (message.startsWith(RESPONSE_MESSAGE)) {
                    // 解析设备信息
                    val deviceInfo = message.substring(RESPONSE_MESSAGE.length + 1)
                    val parts = deviceInfo.split("|")

                    if (parts.size >= 3) {
                        val name = parts[0]
                        val platform = parts[1]
                        val deviceId = parts[2]

                        addDevice(deviceId, name, address, platform)
                    }
                }

            } catch (e: SocketTimeoutException) {
                // 超时是正常的，继续监听
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "监听响应失败", e)
                }
            }
        }
    }

    /**
     * 添加或更新设备
     */
    private fun addDevice(id: String, name: String, address: String, platform: String) {
        val device = PhoneDevice(
            id = id,
            name = name,
            address = address,
            platform = platform
        )

        val isNew = !discoveredDevices.containsKey(id)
        discoveredDevices[id] = device

        if (isNew) {
            Log.i(TAG, "发现新设备: $name ($address)")
            onDeviceListChanged?.invoke(getDeviceList())
        } else {
            // 更新最后发现时间
            Log.d(TAG, "更新设备: $name ($address)")
        }
    }

    /**
     * 获取设备列表
     */
    fun getDeviceList(): List<PhoneDevice> {
        return discoveredDevices.values.toList()
    }

    /**
     * 选择设备
     */
    fun selectDevice(deviceId: String) {
        discoveredDevices.values.forEach { it.selected = (it.id == deviceId) }
        onDeviceListChanged?.invoke(getDeviceList())

        val selectedDevice = discoveredDevices[deviceId]
        if (selectedDevice != null) {
            Log.i(TAG, "已选择设备: ${selectedDevice.name} (${selectedDevice.address})")
        }
    }

    /**
     * 取消选择设备
     */
    fun deselectDevice() {
        discoveredDevices.values.forEach { it.selected = false }
        onDeviceListChanged?.invoke(getDeviceList())
        Log.i(TAG, "已取消设备选择")
    }

    /**
     * 获取当前选中的设备
     */
    fun getSelectedDevice(): PhoneDevice? {
        return discoveredDevices.values.firstOrNull { it.selected }
    }

    /**
     * 停止设备发现服务
     */
    fun stop() {
        Log.i(TAG, "正在停止PhoneDiscoveryServer...")
        isRunning = false

        discoverySocket?.close()
        discoveryScope.cancel()

        multicastLock?.release()

        discoveredDevices.clear()

        Log.i(TAG, "PhoneDiscoveryServer已停止")
    }

    /**
     * 清理超时设备（超过30秒未发现）
     */
    private fun cleanupStaleDevices() {
        val now = System.currentTimeMillis()
        val staleThreshold = 30000L  // 30秒

        val staleDevices = discoveredDevices.filterValues { now - it.lastSeen > staleThreshold }

        if (staleDevices.isNotEmpty()) {
            staleDevices.forEach { (id, device) ->
                Log.d(TAG, "移除超时设备: ${device.name} (${device.address})")
                discoveredDevices.remove(id)
            }

            onDeviceListChanged?.invoke(getDeviceList())
        }
    }
}
