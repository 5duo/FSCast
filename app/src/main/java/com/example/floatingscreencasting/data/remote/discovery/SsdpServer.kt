package com.example.floatingscreencasting.data.remote.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.util.Log
import kotlinx.coroutines.*
import java.net.*
import java.util.*

/**
 * SSDP服务器
 * 负责通过多播发送NOTIFY消息，让其他设备发现我们
 */
class SsdpServer(private val context: Context) {

    companion object {
        private const val TAG = "SsdpServer"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_SEARCH_RESPONSE = "M-SEARCH * HTTP/1.1"
    }

    private var multicastSocket: MulticastSocket? = null
    private var multicastLock: MulticastLock? = null
    private var isRunning = false
    private var listenJob: Job? = null
    private var notifyJob: Job? = null

    // 设备信息 - 使用小米电视信息
    private val deviceUuid = "583f8100-1de2-11db-8981-000c298458a8"
    private val usn = "uuid:$deviceUuid::urn:schemas-upnp-org:device:MediaRenderer:1"
    private val location = "http://${getLocalIpAddress()}:49152/dlna/description.xml"
    private val server = "MI TV/1.0 UPnP/1.0 DLNADOC/1.50"

    /**
     * 启动SSDP服务
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== SsdpServer.start开始 ==========")
        try {
            // 获取WiFi多播锁
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("DLNA_SSDP").apply {
                setReferenceCounted(true)
                acquire()
            }

            Log.d(TAG, "✓ WiFi多播锁已获取")
            Log.d(TAG, "WiFi状态: ${wifiManager.wifiState}")

            // 打印所有网络接口
            Log.d(TAG, "系统网络接口:")
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                for (networkInterface in Collections.list(interfaces)) {
                    Log.d(TAG, "  - ${networkInterface.name}: " +
                            "up=${networkInterface.isUp}, " +
                            "loopback=${networkInterface.isLoopback}, " +
                            "multicast=${networkInterface.supportsMulticast()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取网络接口列表失败", e)
            }

            var socketCreated = false
            try {
                // 创建单个MulticastSocket，绑定到1900端口
                Log.d(TAG, "创建MulticastSocket，绑定到端口$SSDP_PORT...")
                multicastSocket = MulticastSocket(SSDP_PORT).apply {
                    setReuseAddress(true)
                    setBroadcast(true)
                    soTimeout = 5000

                    // 加入多播组
                    val group = InetAddress.getByName(SSDP_ADDRESS)
                    joinGroup(group)
                    Log.d(TAG, "✓ 已加入多播组: $SSDP_ADDRESS")

                    // 设置网络接口为wlan1（如果可能）
                    try {
                        val wlan1 = NetworkInterface.getByName("wlan1")
                        if (wlan1 != null) {
                            networkInterface = wlan1
                            Log.d(TAG, "✓ 已设置网络接口为wlan1")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "无法设置wlan1接口: ${e.message}")
                    }
                }
                Log.d(TAG, "✓ MulticastSocket已绑定到端口1900")
                Log.d(TAG, "Socket本地地址: ${multicastSocket?.localAddress}:${multicastSocket?.localPort}")
                socketCreated = true
            } catch (e: Exception) {
                Log.e(TAG, "✗ 创建MulticastSocket失败", e)
            }

            isRunning = true

            // 只有在Socket创建成功时才监听M-SEARCH
            if (socketCreated && multicastSocket != null) {
                // 启动监听M-SEARCH的协程
                listenJob = launch(Dispatchers.IO) {
                    listenForMSearch()
                }
                Log.d(TAG, "✓ M-SEARCH监听协程已启动")
            } else {
                Log.w(TAG, "✗ Socket创建失败，无法监听M-SEARCH")
            }

            // 启动发送NOTIFY的协程
            notifyJob = launch(Dispatchers.IO) {
                sendNotifyPeriodically()
            }
            Log.d(TAG, "✓ NOTIFY发送协程已启动")

            val currentIp = getLocalIpAddress()
            Log.d(TAG, "========== SSDP服务启动完成 ==========")
            Log.d(TAG, "设备位置: http://$currentIp:49152/dlna/description.xml")
            Log.d(TAG, "设备名称: 智能电视")
            Log.d(TAG, "Device UUID: $deviceUuid")
            Log.d(TAG, "========== SSDP.start()即将返回true ==========")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ 启动SSDP服务失败", e)
            false
        }
    }

    /**
     * 停止SSDP服务
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        isRunning = false

        listenJob?.cancel()
        notifyJob?.cancel()

        try {
            multicastSocket?.leaveGroup(InetAddress.getByName(SSDP_ADDRESS))
            multicastSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "停止SSDP服务器失败", e)
        }

        multicastSocket = null

        // 释放WiFi多播锁
        try {
            multicastLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "释放多播锁失败", e)
        }

        multicastLock = null
        Log.d(TAG, "SSDP服务器已停止")
    }

    /**
     * 监听M-SEARCH请求并响应
     */
    private suspend fun listenForMSearch() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isRunning && multicastSocket != null) {
            try {
                multicastSocket?.receive(packet)

                val message = String(packet.data, 0, packet.length)
                if (message.contains(SSDP_SEARCH_RESPONSE)) {
                    Log.d(TAG, "收到M-SEARCH请求")
                    sendMSearchResponse(packet.address, packet.port)
                }
            } catch (e: SocketTimeoutException) {
                // 超时是正常的，继续循环
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "监听M-SEARCH失败", e)
                }
            }
        }
    }

    /**
     * 发送M-SEARCH响应
     */
    private fun sendMSearchResponse(address: InetAddress, port: Int) {
        val response = """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age=1800
            EXT:
            LOCATION: $location
            SERVER: $server
            ST: urn:schemas-upnp-org:device:MediaRenderer:1
            USN: $usn
            Content-Length: 0

        """.trimIndent().replace("\n", "\r\n")

        val data = response.toByteArray()
        val packet = DatagramPacket(
            data,
            data.size,
            address,
            port
        )

        try {
            multicastSocket?.send(packet)
            Log.d(TAG, "已发送M-SEARCH响应到 ${address.hostAddress}:$port")
        } catch (e: Exception) {
            Log.e(TAG, "发送M-SEARCH响应失败", e)
        }
    }

    /**
     * 定期发送NOTIFY消息
     */
    private suspend fun sendNotifyPeriodically() = withContext(Dispatchers.IO) {
        while (isRunning) {
            sendNotify()
            delay(5000) // 每5秒发送一次
        }
    }

    /**
     * 发送NOTIFY消息
     */
    private fun sendNotify() {
        // 动态获取当前IP地址
        val currentIp = getLocalIpAddress()
        val currentLocation = "http://$currentIp:49152/dlna/description.xml"

        Log.d(TAG, "========== sendNotify开始 ==========")
        Log.d(TAG, "当前IP: $currentIp")
        Log.d(TAG, "LOCATION: $currentLocation")

        // 发送两次NOTIFY：一次是root device，一次是MediaRenderer
        val notifyMessages = listOf(
            // Root device NOTIFY
            "NOTIFY * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: $currentLocation\r\n" +
            "SERVER: $server\r\n" +
            "NT: upnp:rootdevice\r\n" +
            "NTS: ssdp:alive\r\n" +
            "USN: uuid:$deviceUuid::upnp:rootdevice\r\n" +
            "Content-Length: 0\r\n\r\n",
            // MediaRenderer NOTIFY
            "NOTIFY * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: $currentLocation\r\n" +
            "SERVER: $server\r\n" +
            "NT: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
            "NTS: ssdp:alive\r\n" +
            "USN: $usn\r\n" +
            "Content-Length: 0\r\n\r\n"
        )

        for ((index, notifyMsg) in notifyMessages.withIndex()) {
            try {
                val data = notifyMsg.toByteArray(Charsets.US_ASCII)
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetSocketAddress(SSDP_ADDRESS, SSDP_PORT)
                )

                // 打印NOTIFY消息内容（前200字符）
                Log.d(TAG, "NOTIFY消息 #$index 内容:")
                Log.d(TAG, notifyMsg.take(200))

                multicastSocket?.send(packet)
                Log.d(TAG, "✓ 发送NOTIFY#$index 成功 (${data.size} 字节)")
            } catch (e: Exception) {
                Log.e(TAG, "✗ 发送NOTIFY#$index 失败: ${e.message}", e)
            }
        }
        Log.d(TAG, "========== sendNotify完成 ==========")
    }

    /**
     * 获取WiFi网络接口
     */
    private fun getWifiInterface(wifiManager: WifiManager): NetworkInterface? {
        try {
            val ipAddress = wifiManager.connectionInfo.ipAddress
            val interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is InetAddress && !address.isLoopbackAddress) {
                        val addrBytes = address.address
                        if (addrBytes.size == 4) {
                            // 检查是否是WiFi接口
                            val addrStr = String.format(
                                "%d.%d.%d.%d",
                                addrBytes[0].toInt() and 0xFF,
                                addrBytes[1].toInt() and 0xFF,
                                addrBytes[2].toInt() and 0xFF,
                                addrBytes[3].toInt() and 0xFF
                            )
                            val ipAddrStr = String.format(
                                "%d.%d.%d.%d",
                                ipAddress and 0xFF,
                                ipAddress shr 8 and 0xFF,
                                ipAddress shr 16 and 0xFF,
                                ipAddress shr 24 and 0xFF
                            )
                            if (addrStr == ipAddrStr) {
                                Log.d(TAG, "找到WiFi接口: ${networkInterface.name}")
                                return networkInterface
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取WiFi接口失败", e)
        }
        return null
    }

    /**
     * 获取本机IP地址
     */
    private fun getLocalIpAddress(): String {
        try {
            // 方法1：遍历所有网络接口，找到非回环的IPv4地址
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    // 优先使用wlan接口（WiFi）
                    val interfaceName = networkInterface.name.lowercase()
                    if (interfaceName.contains("wlan") || interfaceName.contains("wifi")) {
                        val addresses = Collections.list(networkInterface.inetAddresses)
                        for (address in addresses) {
                            if (!address.isLoopbackAddress && address is Inet4Address) {
                                val ip = address.hostAddress ?: ""
                                Log.d(TAG, "找到WiFi IP地址: $ip (接口: ${networkInterface.name})")
                                return ip
                            }
                        }
                    }
                }
            }

            // 方法2：如果没有找到wlan接口，使用第一个非回环地址
            for (networkInterface in Collections.list(interfaces)) {
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val ip = address.hostAddress ?: ""
                            Log.d(TAG, "使用IP地址: $ip (接口: ${networkInterface.name})")
                            return ip
                        }
                    }
                }
            }

            Log.e(TAG, "未找到有效的网络接口")
            return "192.168.112.222" // 使用车机的已知IP
        } catch (e: Exception) {
            Log.e(TAG, "获取本机IP失败", e)
            return "192.168.112.222" // 使用车机的已知IP
        }
    }
}
