package com.example.fscastremote.service

import android.util.Log
import com.example.fscastremote.common.Constants
import com.example.fscastremote.model.ConnectionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val TAG = "DiscoveryClient"

/**
 * UDP 发现客户端
 */
class DiscoveryClient {

    suspend fun discover(timeoutMs: Long = Constants.DISCOVER_TIMEOUT_MS): List<ConnectionInfo> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<ConnectionInfo>()
            val socket = DatagramSocket(null).apply {
                broadcast = true
                bind(java.net.InetSocketAddress(0))
            }

            try {
                withTimeout(timeoutMs) {
                    val discoverData = Constants.DISCOVER_MESSAGE
                    val packet = DatagramPacket(
                        discoverData,
                        discoverData.size,
                        InetAddress.getByName("255.255.255.255"),
                        Constants.UDP_PORT
                    )
                    socket.send(packet)
                    Log.i(TAG, "Discover broadcast sent")

                    val buffer = ByteArray(1024)
                    val endTime = System.currentTimeMillis() + timeoutMs

                    while (System.currentTimeMillis() < endTime) {
                        socket.soTimeout = 500
                        try {
                            val receivePacket = DatagramPacket(buffer, buffer.size)
                            socket.receive(receivePacket)
                            val response = String(receivePacket.data, 0, receivePacket.length)
                            Log.i(TAG, "UDP received: $response")

                            parseDiscoveryResponse(response)?.let { info ->
                                if (!results.any { it.ip == info.ip }) {
                                    results.add(info)
                                }
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            continue
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.i(TAG, "Discover completed, found ${results.size} device(s)")
            } finally {
                socket.close()
            }

            results
        }
    }

    private fun parseDiscoveryResponse(json: String): ConnectionInfo? {
        return try {
            val type = extractJsonValue(json, "type") ?: return null
            if (type != "\"discover_response\"") return null

            val name = extractJsonValue(json, "name")?.removeSurrounding("\"") ?: "FSCast"
            val ip = extractJsonValue(json, "ip")?.removeSurrounding("\"") ?: return null
            val port = extractJsonValue(json, "port")?.toIntOrNull() ?: Constants.TCP_PORT
            val version = extractJsonValue(json, "version")?.toIntOrNull() ?: 1

            ConnectionInfo(name, ip, port, version)
        } catch (e: Exception) {
            Log.e(TAG, "Parse discovery response failed: ${e.message}")
            null
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*([^,}\\}]*)"
        val regex = Regex(pattern)
        val match = regex.find(json) ?: return null
        return match.groupValues[1].trim()
    }
}
