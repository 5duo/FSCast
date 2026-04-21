package com.example.floatingscreencasting.websocket

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

/**
 * 车机端WebSocket服务器
 * 用于与手机端建立双向连接
 */
class CarWebSocketServer(private val port: Int) : WebSocketServer(InetSocketAddress("0.0.0.0", port)) {

    companion object {
        private const val TAG = "CarWebSocketServer"
    }

    // 服务器运行状态
    @Volatile
    var isRunning: Boolean = false
        private set

    // 已连接的客户端（使用线程安全的ConcurrentHashMap）
    private val clients = java.util.concurrent.ConcurrentHashMap<String, WebSocket>()

    // 消息序列号生成器（用于进度同步的去重和乱序处理）
    private val messageSequenceNumber = AtomicLong(0)

    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 消息回调
    var onMessageReceived: ((String, WebSocket) -> Unit)? = null

    // 连接状态回调
    var onClientConnected: ((String) -> Unit)? = null
    var onClientDisconnected: ((String) -> Unit)? = null

    // 控制命令回调（来自手机端）
    var onControlCommand: ((action: String, data: org.json.JSONObject, syncId: String, clientId: String) -> Unit)? = null

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

        val clientId = conn.remoteSocketAddress.address.hostAddress ?: return
        clients[clientId] = conn

        Log.i(TAG, "客户端已连接: $clientId")

        _connectionState.value = ConnectionState.Connected(clients.size)
        onClientConnected?.invoke(clientId)

        // 发送欢迎消息（包含时间戳，用于手机端时间校准）
        val currentTime = System.currentTimeMillis()
        val welcomeJson = org.json.JSONObject()
        welcomeJson.put("type", "welcome")
        val data = org.json.JSONObject()
        data.put("message", "已连接到车机")
        data.put("timestamp", currentTime)  // ⏱️ timestamp放在data对象里，与模拟器保持一致
        welcomeJson.put("data", data)
        sendToClient(clientId, welcomeJson.toString())
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        conn ?: return

        val clientId = conn.remoteSocketAddress.address.hostAddress ?: return
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

        val clientId = conn.remoteSocketAddress.address.hostAddress ?: "unknown"
        Log.d(TAG, "收到消息 from $clientId: $message")

        try {
            val json = org.json.JSONObject(message)
            val type = json.optString("type")

            when (type) {
                "control_command" -> {
                    // 处理来自手机端的控制命令
                    handleControlCommand(json, conn, clientId)
                }
                else -> {
                    // 其他消息类型使用现有回调处理
                    onMessageReceived?.invoke(message, conn)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析消息失败: ${e.message}, 消息内容: $message")
            // 解析失败时，使用现有回调处理
            onMessageReceived?.invoke(message, conn)
        }
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
        // 显示消息的前100个字符，方便调试
        val preview = if (message.length > 100) message.substring(0, 100) + "..." else message
        Log.d(TAG, "广播消息到 $successCount 个客户端: $preview")
        return successCount
    }

    /**
     * 发送播放命令到手机端
     */
    fun sendPlayCommand(uri: String, httpHeaders: Map<String, String> = emptyMap()): Int {
        Log.i(TAG, "sendPlayCommand: URI长度=${uri.length}, HTTP头数量=${httpHeaders.size}")
        val command = buildJsonCommand("play") {
            put("uri", uri)
            put("headers", httpHeaders)
        }
        Log.i(TAG, "发送播放命令JSON: ${command}")
        return broadcastMessage(command)
    }

    /**
     * 发送播放命令+进度到手机端（用于同步启动）
     * 手机端收到后会加载视频、跳到指定进度、然后暂停等待
     */
    fun sendPlayCommandWithPosition(uri: String, httpHeaders: Map<String, String>, positionMs: Long): Int {
        Log.i(TAG, "sendPlayCommandWithPosition: URI长度=${uri.length}, 进度=${positionMs}ms, HTTP头数量=${httpHeaders.size}")
        val command = buildJsonCommand("play_and_seek") {
            put("uri", uri)
            put("headers", httpHeaders)
            put("position", positionMs)
        }
        Log.i(TAG, "发送播放命令JSON: ${command}")
        return broadcastMessage(command)
    }

    /**
     * 发送"恢复播放"命令到手机端
     * 用于同步启动流程：手机端加载并跳转完成后，车机发送此命令让两端同时播放
     */
    fun sendResumeCommand(): Int {
        Log.i(TAG, "sendResumeCommand: 发送恢复播放命令")
        val command = buildJsonCommand("resume") {}
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
     * 添加序列号和时间戳，用于防止消息乱序和去重
     * @param positionMs 车机当前播放位置（毫秒）
     * @param durationMs 视频总时长（毫秒）
     * @param isPlaying 是否正在播放
     */
    fun sendProgressUpdate(positionMs: Long, durationMs: Long, isPlaying: Boolean): Int {
        val sequenceNumber = messageSequenceNumber.incrementAndGet()
        val currentTime = System.currentTimeMillis()

        val json = org.json.JSONObject()
        json.put("type", "command")
        json.put("action", "progress")
        json.put("timestamp", currentTime)

        // 构建命令数据
        val data = org.json.JSONObject()
        data.put("sequenceNumber", sequenceNumber)
        data.put("position", positionMs)
        data.put("duration", durationMs)
        data.put("isPlaying", isPlaying)
        data.put("sendTime", currentTime)  // ⏱️ 添加sendTime字段，用于手机端时间对齐
        json.put("data", data)

        val command = json.toString()
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

    /**
     * 启动WebSocket服务器
     */
    override fun start() {
        try {
            super.start()
            isRunning = true
            Log.i(TAG, "WebSocket服务器已启动，监听端口$port")
        } catch (e: Exception) {
            isRunning = false
            Log.e(TAG, "WebSocket服务器启动失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 停止WebSocket服务器
     */
    override fun stop() {
        isRunning = false

        // 先关闭所有客户端连接
        clients.values.forEach { client ->
            try {
                if (client.isOpen) {
                    client.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "关闭客户端连接失败: ${e.message}")
            }
        }
        clients.clear()

        super.stop()
        Log.i(TAG, "WebSocket服务器已停止")
    }

    /**
     * 处理来自手机端的控制命令
     */
    private fun handleControlCommand(json: org.json.JSONObject, conn: WebSocket?, clientId: String) {
        try {
            val action = json.optString("action")
            val data = json.optJSONObject("data") ?: org.json.JSONObject()
            val syncId = json.optString("syncId")
            val source = json.optString("source", "unknown")

            Log.i(TAG, "收到手机端控制命令: action=$action, syncId=$syncId, source=$source")

            // 验证来源
            if (source != "phone") {
                Log.w(TAG, "忽略非手机端的控制命令")
                return
            }

            // 调用回调处理控制命令
            onControlCommand?.invoke(action, data, syncId, clientId)

        } catch (e: Exception) {
            Log.e(TAG, "处理控制命令失败: ${e.message}")
        }
    }
}
