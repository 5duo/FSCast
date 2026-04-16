package com.example.floatingscreencasting.websocket

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * 车机端WebSocket服务器
 * 用于与手机端建立双向连接
 */
class CarWebSocketServer(private val port: Int) : WebSocketServer(InetSocketAddress(port)) {

    companion object {
        private const val TAG = "CarWebSocketServer"
    }

    // 已连接的客户端
    private val clients = mutableMapOf<String, WebSocket>()

    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 消息回调
    var onMessageReceived: ((String, WebSocket) -> Unit)? = null

    // 连接状态回调
    var onClientConnected: ((String) -> Unit)? = null
    var onClientDisconnected: ((String) -> Unit)? = null

    /**
     * 连接状态
     */
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data class Connected(val clientCount: Int) : ConnectionState()
        data class Error(val error: String) : ConnectionState()
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        conn ?: return

        val clientId = conn.remoteSocketAddress.address.hostAddress
        clients[clientId] = conn

        Log.i(TAG, "客户端已连接: $clientId")

        _connectionState.value = ConnectionState.Connected(clients.size)
        onClientConnected?.invoke(clientId)

        // 发送欢迎消息
        sendToClient(clientId, """{"type":"welcome","message":"已连接到车机"}""")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        conn ?: return

        val clientId = conn.remoteSocketAddress.address.hostAddress
        clients.remove(clientId)

        Log.i(TAG, "客户端已断开: $clientId, 原因: $reason")

        _connectionState.value = if (clients.isEmpty()) {
            ConnectionState.Disconnected
        } else {
            ConnectionState.Connected(clients.size)
        }

        onClientDisconnected?.invoke(clientId)
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        conn ?: return
        message ?: return

        val clientId = conn.remoteSocketAddress.address.hostAddress
        Log.d(TAG, "收到消息 from $clientId: $message")

        onMessageReceived?.invoke(message, conn)
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        val clientId = conn?.remoteSocketAddress?.address?.hostAddress ?: "unknown"

        Log.e(TAG, "WebSocket错误 from $clientId: ${ex?.message}")

        _connectionState.value = ConnectionState.Error(ex?.message ?: "Unknown error")
    }

    override fun onStart() {
        Log.i(TAG, "WebSocket服务器已启动，监听端口: $port")
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * 发送消息到指定客户端
     */
    fun sendToClient(clientId: String, message: String): Boolean {
        val client = clients[clientId]
        return if (client != null && client.isOpen) {
            client.send(message)
            true
        } else {
            Log.w(TAG, "无法发送消息到客户端 $clientId: 客户端不存在或未连接")
            false
        }
    }

    /**
     * 广播消息到所有客户端
     */
    fun broadcastMessage(message: String): Int {
        var successCount = 0
        clients.values.forEach { client ->
            if (client.isOpen) {
                client.send(message)
                successCount++
            }
        }
        Log.d(TAG, "广播消息到 $successCount 个客户端")
        return successCount
    }

    /**
     * 发送播放命令到手机端
     */
    fun sendPlayCommand(uri: String, httpHeaders: Map<String, String> = emptyMap()): Int {
        val command = buildJsonCommand("play") {
            put("uri", uri)
            put("headers", httpHeaders)
        }
        return broadcastMessage(command)
    }

    /**
     * 发送暂停命令到手机端
     */
    fun sendPauseCommand(): Int {
        val command = buildJsonCommand("pause") {}
        return broadcastMessage(command)
    }

    /**
     * 发送停止命令到手机端
     */
    fun sendStopCommand(): Int {
        val command = buildJsonCommand("stop") {}
        return broadcastMessage(command)
    }

    /**
     * 发送跳转命令到手机端
     */
    fun sendSeekCommand(positionMs: Long): Int {
        val command = buildJsonCommand("seek") {
            put("position", positionMs)
        }
        return broadcastMessage(command)
    }

    /**
     * 发送进度同步到手机端
     */
    fun sendProgressUpdate(positionMs: Long, durationMs: Long, isPlaying: Boolean): Int {
        val command = buildJsonCommand("progress") {
            put("position", positionMs)
            put("duration", durationMs)
            put("isPlaying", isPlaying)
        }
        return broadcastMessage(command)
    }

    /**
     * 构建JSON命令
     */
    private fun buildJsonCommand(action: String, build: org.json.JSONObject.() -> Unit): String {
        val json = org.json.JSONObject()
        json.put("type", "command")
        json.put("action", action)
        json.put("timestamp", System.currentTimeMillis())

        // 构建命令数据
        val data = org.json.JSONObject()
        build(data)
        json.put("data", data)

        return json.toString()
    }

    /**
     * 获取已连接的客户端数量
     */
    fun getClientCount(): Int = clients.size

    /**
     * 获取已连接的客户端列表
     */
    fun getConnectedClients(): List<String> = clients.keys.toList()

    /**
     * 检查是否有客户端连接
     */
    fun hasConnectedClients(): Boolean = clients.isNotEmpty()
}
