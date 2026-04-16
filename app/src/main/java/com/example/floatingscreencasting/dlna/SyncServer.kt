package com.example.floatingscreencasting.dlna

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 同步服务器
 * 车机端主控，手机端从设备
 * 负责广播播放状态给手机端，实现音视频同步
 */
class SyncServer(private val port: Int = 19890) {

    companion object {
        private const val TAG = "SyncServer"
    }

    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<ClientConnection>()
    private var isRunning = false
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val gson = Gson()

    /**
     * 客户端连接封装
     */
    private data class ClientConnection(
        val socket: Socket,
        val reader: BufferedReader,
        val id: String
    )

    /**
     * 同步消息类型
     */
    sealed class SyncMessage {
        abstract val type: String

        data class Play(
            val uri: String,
            val position: Long = 0,
            override val type: String = "play"
        ) : SyncMessage()

        data class Pause(
            val position: Long,
            override val type: String = "pause"
        ) : SyncMessage()

        data class Seek(
            val position: Long,
            override val type: String = "seek"
        ) : SyncMessage()

        data class Stop(
            val position: Long,
            override val type: String = "stop"
        ) : SyncMessage()

        data class Progress(
            val position: Long,
            val duration: Long,
            val isPlaying: Boolean,
            val timestamp: Long = System.currentTimeMillis(),
            override val type: String = "progress"
        ) : SyncMessage()

        data class DeviceInfo(
            val deviceId: String,
            val deviceName: String,
            override val type: String = "device_info"
        ) : SyncMessage()
    }

    /**
     * 启动同步服务器
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "SyncServer已经在运行")
            return
        }

        try {
            serverSocket = ServerSocket(port)
            isRunning = true

            Log.i(TAG, "SyncServer启动成功，监听端口: $port")

            // 启动客户端接受协程
            serverScope.launch {
                acceptClients()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动SyncServer失败", e)
            isRunning = false
        }
    }

    /**
     * 接受客户端连接
     */
    private suspend fun acceptClients() {
        while (isRunning) {
            try {
                val clientSocket = serverSocket?.accept() ?: continue

                val clientId = "${clientSocket.inetAddress.hostAddress}:${clientSocket.port}"
                Log.i(TAG, "新客户端连接: $clientId")

                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))

                val client = ClientConnection(clientSocket, reader, clientId)
                clients.add(client)

                // 启动客户端消息处理协程
                handleClient(client)

            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "接受客户端连接失败", e)
                }
            }
        }
    }

    /**
     * 处理客户端消息
     */
    private suspend fun handleClient(client: ClientConnection) {
        serverScope.launch {
            try {
                while (isRunning && clients.contains(client)) {
                    val line = client.reader.readLine() ?: break

                    if (line.isNotBlank()) {
                        handleMessage(line, client)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理客户端消息失败: ${client.id}", e)
            } finally {
                removeClient(client)
            }
        }
    }

    /**
     * 处理来自客户端的消息
     */
    private suspend fun handleMessage(message: String, sender: ClientConnection) {
        try {
            val json = gson.fromJson(message, Map::class.java)
            val type = json["type"] as? String ?: return

            Log.d(TAG, "收到消息: type=$type, from=${sender.id}")

            when (type) {
                "device_info" -> {
                    // 手机端设备信息
                    val deviceName = json["deviceName"] as? String ?: "Unknown"
                    Log.i(TAG, "手机设备连接: ${sender.id}, 名称: $deviceName")

                    // 回复车机设备信息
                    sendToClient(sender, gson.toJson(mapOf(
                        "type" to "device_info_ack",
                        "deviceId" to "fscast_car",
                        "deviceName" to "FSCast 车机"
                    )))
                }
                "play" -> {
                    // 手机端请求播放
                    val uri = json["uri"] as? String
                    val position = (json["position"] as? Double)?.toLong() ?: 0L
                    Log.i(TAG, "手机请求播放: uri=$uri, position=$position")
                }
                "pause" -> {
                    // 手机端请求暂停
                    val position = (json["position"] as? Double)?.toLong() ?: 0L
                    Log.i(TAG, "手机请求暂停: position=$position")
                }
                "seek" -> {
                    // 手机端请求跳转
                    val position = (json["position"] as? Double)?.toLong() ?: 0L
                    Log.i(TAG, "手机请求跳转: position=$position")
                }
                else -> {
                    Log.w(TAG, "未知消息类型: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析消息失败: $message", e)
        }
    }

    /**
     * 广播消息给所有客户端
     */
    fun broadcast(message: SyncMessage) {
        if (clients.isEmpty()) {
            return
        }

        try {
            val json = gson.toJson(message)
            Log.d(TAG, "广播消息: ${message.type}, 客户端数量: ${clients.size}")

            clients.toList().forEach { client ->
                sendToClient(client, json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "广播消息失败", e)
        }
    }

    /**
     * 发送消息给指定客户端
     */
    private fun sendToClient(client: ClientConnection, message: String) {
        try {
            client.socket.getOutputStream().write((message + "\n").toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败: ${client.id}", e)
            removeClient(client)
        }
    }

    /**
     * 移除客户端
     */
    private fun removeClient(client: ClientConnection) {
        if (clients.remove(client)) {
            Log.i(TAG, "客户端断开: ${client.id}")
            try {
                client.socket.close()
            } catch (e: Exception) {
                // 忽略关闭错误
            }
        }
    }

    /**
     * 获取已连接的客户端列表
     */
    fun getConnectedClients(): List<Map<String, String>> {
        return clients.map { mapOf("id" to it.id) }
    }

    /**
     * 获取已连接的客户端数量
     */
    fun getClientCount(): Int = clients.size

    /**
     * 停止同步服务器
     */
    fun stop() {
        Log.i(TAG, "正在停止SyncServer...")
        isRunning = false

        clients.forEach { client ->
            try {
                client.socket.close()
            } catch (e: Exception) {
                // 忽略关闭错误
            }
        }
        clients.clear()

        serverSocket?.close()
        serverScope.cancel()

        Log.i(TAG, "SyncServer已停止")
    }
}
