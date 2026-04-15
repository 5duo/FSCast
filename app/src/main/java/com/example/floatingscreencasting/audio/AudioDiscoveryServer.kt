package com.example.floatingscreencasting.audio

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.*
import java.nio.charset.StandardCharsets

/**
 * UDP 发现服务，让手机客户端在局域网中自动发现车机。
 *
 * 手机端广播 "FSCAST_DISCOVER" 到 255.255.255.255:19876，
 * 本服务监听并回复车机的 IP 和端口信息。
 */
class AudioDiscoveryServer {
    companion object {
        private const val TAG = "AudioDiscoveryServer"
        const val UDP_PORT = 19876
        const val DISCOVER_MAGIC = "FSCAST_DISCOVER"
    }

    private var datagramSocket: DatagramSocket? = null
    private var discoverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var running = false

    /**
     * 启动 UDP 发现服务。
     */
    fun start() {
        if (running) {
            Log.w(TAG, "发现服务已在运行")
            return
        }

        try {
            datagramSocket = DatagramSocket(UDP_PORT).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1000 // 1秒超时，用于检查 running 状态
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动 UDP 发现服务失败", e)
            return
        }

        running = true
        discoverJob = scope.launch {
            Log.i(TAG, "UDP 发现服务启动，端口: $UDP_PORT")
            val receiveBuffer = ByteArray(256)

            while (running && isActive) {
                try {
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    datagramSocket?.receive(packet) ?: break

                    val message = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()

                    if (message == DISCOVER_MAGIC) {
                        val senderAddress = packet.address.hostAddress
                        Log.d(TAG, "收到发现请求: $senderAddress")
                        sendDiscoveryResponse(packet.address, packet.port)
                    }
                } catch (e: SocketTimeoutException) {
                    // 超时正常，继续循环检查 running 状态
                } catch (e: PortUnreachableException) {
                    // 忽略
                } catch (e: Exception) {
                    if (running) {
                        Log.e(TAG, "UDP 接收异常", e)
                    }
                }
            }

            Log.i(TAG, "UDP 发现服务已停止")
        }
    }

    /**
     * 停止 UDP 发现服务。
     */
    fun stop() {
        running = false
        datagramSocket?.close()
        datagramSocket = null
        discoverJob?.cancel()
        scope.cancel()
        Log.i(TAG, "UDP 发现服务停止")
    }

    /**
     * 发送发现响应给请求方。
     */
    private fun sendDiscoveryResponse(targetAddress: InetAddress, targetPort: Int) {
        try {
            val localIp = getLocalIpAddress() ?: return

            val json = JSONObject().apply {
                put("type", "discover_response")
                put("name", "FSCast")
                put("ip", localIp)
                put("port", AudioStreamServer.TCP_PORT)
                put("version", 1)
            }

            val responseBytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            val responsePacket = DatagramPacket(
                responseBytes,
                responseBytes.size,
                targetAddress,
                targetPort
            )

            datagramSocket?.send(responsePacket)
            Log.d(TAG, "已发送发现响应: $localIp -> ${targetAddress.hostAddress}:$targetPort")
        } catch (e: Exception) {
            Log.e(TAG, "发送发现响应失败", e)
        }
    }

    /**
     * 获取本机 WiFi 局域网 IP 地址。
     */
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                // 跳过回环和未启用接口
                if (intf.isLoopback || !intf.isUp) continue

                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        // 优先返回 192.168.x.x 或 10.x.x.x 等局域网地址
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
            // 如果没找到局域网地址，返回任意非回环 IPv4
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本机 IP 失败", e)
        }
        return null
    }

    fun isRunning(): Boolean = running && datagramSocket?.isBound == true
}
