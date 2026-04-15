package com.example.floatingscreencasting.audio

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TCP 服务器，支持多设备同时连接。
 *
 * 功能：
 * - 接受多个手机客户端连接（最多 5 个）
 * - 向选中的设备发送 PCM 音频流
 * - 向所有设备广播播放状态
 * - 接收所有设备的控制命令
 */
class AudioStreamServer(
    private val pcmRingBuffer: PcmRingBuffer,
    private val commandHandler: (clientId: String, action: String, params: Map<String, Any>) -> Unit,
    private val stateProvider: () -> PlaybackState
) {
    companion object {
        private const val TAG = "AudioStreamServer"
        const val TCP_PORT = 19880
        const val MAX_CLIENTS = 5
        const val HEARTBEAT_INTERVAL_MS = 3000L
        const val STATE_SYNC_INTERVAL_MS = 1000L

        // 帧类型
        const val FRAME_HEARTBEAT: Byte = 0x00
        const val FRAME_FORMAT_HEADER: Byte = 0x01
        const val FRAME_PCM_DATA: Byte = 0x02
        const val FRAME_FORMAT_CHANGE: Byte = 0x03
        const val FRAME_STATE_SYNC: Byte = 0x10
        const val FRAME_COMMAND_RESULT: Byte = 0x11
        const val FRAME_OUTPUT_CHANGED: Byte = 0x12
        const val FRAME_CONTROL_COMMAND: Byte = 0x20
    }

    data class ConnectedClient(
        val id: String,
        val socket: Socket,
        val name: String,
        val platform: String,
        val address: String
    )

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val title: String = "",
        val audioOutput: String = "speaker",
        val selectedDevice: String? = null
    )

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var stateSyncJob: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 客户端管理
    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients

    private val clients = mutableMapOf<String, ClientConnection>()
    private var _selectedClientId: String? = null
    var selectedClientId: String? = null
        private set

    // 当前音频格式
    @Volatile
    private var currentSampleRate: Int = 0
    @Volatile
    private var currentChannels: Int = 0
    @Volatile
    private var currentEncoding: Int = 0

    var onClientListChanged: ((clients: List<ConnectedClient>) -> Unit)? = null

    private val readBuffer = ByteArray(8192)

    private inner class ClientConnection(
        val client: ConnectedClient,
        val output: OutputStream,
        val input: InputStream
    ) {
        var receiveJob: Job? = null
        var sendJob: Job? = null
        var isActive = true
    }

    /**
     * 启动 TCP 服务器
     */
    fun start() {
        if (serverSocket != null) {
            Log.w(TAG, "服务器已在运行")
            return
        }

        try {
            serverSocket = ServerSocket(TCP_PORT)
            Log.i(TAG, "TCP 服务器启动，端口: $TCP_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "启动 TCP 服务器失败", e)
            return
        }

        // 接受客户端连接
        serverJob = scope.launch {
            while (isActive) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    socket.tcpNoDelay = true
                    socket.soTimeout = 0
                    handleNewClient(socket)
                } catch (e: SocketException) {
                    if (isActive) Log.e(TAG, "接受连接异常", e)
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "接受连接异常", e)
                }
            }
        }

        // 状态同步
        stateSyncJob = scope.launch {
            while (isActive) {
                try {
                    broadcastState()
                } catch (e: Exception) {
                    Log.e(TAG, "状态同步异常", e)
                }
                delay(STATE_SYNC_INTERVAL_MS)
            }
        }

        // 心跳 + PCM 发送
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    sendHeartbeats()
                    sendPcmToSelected()
                } catch (e: Exception) {
                    Log.e(TAG, "心跳/PCM发送异常", e)
                }
                delay(50) // 50ms 发送间隔
            }
        }
    }

    /**
     * 停止服务器
     */
    fun stop() {
        Log.i(TAG, "停止 TCP 服务器")
        scope.launch {
            // 关闭所有客户端连接
            clients.values.toList().forEach { conn ->
                removeClient(conn.client.id)
            }

            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            stateSyncJob?.cancel()
            heartbeatJob?.cancel()
            scope.cancel()
            Log.i(TAG, "TCP 服务器已停止")
        }
    }

    /**
     * 处理新客户端连接
     */
    private suspend fun handleNewClient(socket: Socket) {
        val address = socket.remoteSocketAddress.toString()
        Log.i(TAG, "新连接: $address")

        if (clients.size >= MAX_CLIENTS) {
            Log.w(TAG, "已达最大连接数 $MAX_CLIENTS，拒绝: $address")
            socket.close()
            return
        }

        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 发送握手
            val helloJson = JSONObject().apply {
                put("type", "hello")
                put("version", 1)
            }
            sendJson(output, helloJson)

            // 等待客户端握手确认（超时 3 秒）
            val helloAck = readJson(input, 3000)
            if (helloAck == null || helloAck.optString("type") != "hello_ack") {
                Log.w(TAG, "握手失败: $address")
                socket.close()
                return
            }

            val deviceName = helloAck.optString("device_name", "未知设备")
            val platform = helloAck.optString("platform", "unknown")
            val clientId = address

            val client = ConnectedClient(
                id = clientId,
                socket = socket,
                name = deviceName,
                platform = platform,
                address = address
            )

            val conn = ClientConnection(client, output, input)
            clients[clientId] = conn
            updateClientList()

            // 如果有当前音频格式，发送给新客户端
            if (currentSampleRate > 0 && _selectedClientId == clientId) {
                sendFormatHeader(output, currentSampleRate, currentChannels, currentEncoding)
            }

            // 启动接收循环
            conn.receiveJob = scope.launch {
                receiveLoop(conn)
            }

            Log.i(TAG, "客户端已连接: $deviceName ($platform) $address")

        } catch (e: Exception) {
            Log.e(TAG, "处理新客户端异常: $address", e)
            socket.close()
        }
    }

    /**
     * 客户端接收循环
     */
    private suspend fun receiveLoop(conn: ClientConnection) {
        try {
            val buffer = ByteArray(4096)
            val stream = conn.input

            while (conn.isActive && conn.client.socket.isConnected) {
                // 读取帧类型
                val frameType = stream.read()
                if (frameType == -1) {
                    Log.i(TAG, "客户端断开: ${conn.client.name}")
                    break
                }

                when (frameType.toInt()) {
                    FRAME_HEARTBEAT.toInt() -> {
                        // 心跳，无需处理
                    }
                    FRAME_CONTROL_COMMAND.toInt() -> {
                        // 读取 JSON 命令
                        val json = readJsonFrame(stream)
                        if (json != null) {
                            handleCommand(conn.client.id, json)
                        }
                    }
                    else -> {
                        Log.w(TAG, "未知帧类型: 0x${frameType.toString(16)}")
                    }
                }
            }
        } catch (e: SocketException) {
            Log.i(TAG, "客户端连接异常: ${conn.client.name}", e)
        } catch (e: Exception) {
            Log.e(TAG, "接收循环异常: ${conn.client.name}", e)
        } finally {
            removeClient(conn.client.id)
        }
    }

    /**
     * 处理控制命令
     */
    private fun handleCommand(clientId: String, json: JSONObject) {
        val action = json.optString("action", "")
        if (action.isEmpty()) return

        Log.d(TAG, "收到命令: clientId=$clientId, action=$action")

        val params = mutableMapOf<String, Any>()
        if (json.has("position_ms")) params["position_ms"] = json.getLong("position_ms")
        if (json.has("output")) params["output"] = json.getString("output")

        commandHandler(clientId, action, params)
    }

    /**
     * 选择指定客户端接收音频
     */
    fun selectClient(clientId: String?) {
        val oldId = _selectedClientId

        if (oldId == clientId) return // 无变化

        // 通知旧设备取消
        if (oldId != null) {
            val oldConn = clients[oldId]
            if (oldConn != null) {
                sendOutputChanged(oldConn.output, "speaker")
            }
        }

        _selectedClientId = clientId
        selectedClientId = clientId

        // 通知新设备选中
        if (clientId != null) {
            val newConn = clients[clientId]
            if (newConn != null) {
                sendOutputChanged(newConn.output, "phone")
                // 发送当前音频格式
                if (currentSampleRate > 0) {
                    sendFormatHeader(newConn.output, currentSampleRate, currentChannels, currentEncoding)
                }
            }
        }

        Log.i(TAG, "音频输出切换: $oldId → $clientId")
        updateClientList()
    }

    /**
     * 断开指定客户端
     */
    fun disconnectClient(clientId: String) {
        removeClient(clientId)
    }

    /**
     * 发送音频格式头
     */
    fun sendFormatHeader(sampleRate: Int, channels: Int, encoding: Int) {
        currentSampleRate = sampleRate
        currentChannels = channels
        currentEncoding = encoding

        val selectedId = _selectedClientId ?: return
        val conn = clients[selectedId] ?: return

        try {
            sendFormatHeader(conn.output, sampleRate, channels, encoding)
        } catch (e: Exception) {
            Log.e(TAG, "发送格式头失败", e)
        }
    }

    /**
     * 发送命令执行结果给指定客户端
     */
    fun sendCommandResult(clientId: String, action: String, success: Boolean, extra: Map<String, Any> = emptyMap()) {
        val conn = clients[clientId] ?: return
        try {
            val json = JSONObject().apply {
                put("type", "command_result")
                put("action", action)
                put("success", success)
                extra.forEach { (k, v) -> put(k, v) }
            }
            sendJson(conn.output, json, FRAME_COMMAND_RESULT)
        } catch (e: Exception) {
            Log.e(TAG, "发送命令结果失败", e)
        }
    }

    // ==================== 内部方法 ====================

    private fun sendFormatHeader(output: OutputStream, sampleRate: Int, channels: Int, encoding: Int) {
        synchronized(output) {
            output.write(FRAME_FORMAT_HEADER.toInt())
            val buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            buf.putInt(sampleRate)
            buf.putInt(channels)
            buf.putInt(encoding)
            output.write(buf.array())
            output.flush()
        }
    }

    private fun sendPcmToSelected() {
        val selectedId = _selectedClientId ?: return
        val conn = clients[selectedId] ?: return
        if (!conn.isActive) return

        val available = pcmRingBuffer.available()
        if (available == 0) return

        val toRead = available.coerceAtMost(readBuffer.size)
        val bytesRead = pcmRingBuffer.read(readBuffer, timeoutMs = 0)
        if (bytesRead <= 0) return

        try {
            synchronized(conn.output) {
                conn.output.write(FRAME_PCM_DATA.toInt())
                val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                lenBuf.putInt(bytesRead)
                conn.output.write(lenBuf.array())
                conn.output.write(readBuffer, 0, bytesRead)
                conn.output.flush()
            }
        } catch (e: SocketException) {
            Log.w(TAG, "PCM 发送失败，客户端可能已断开: ${conn.client.name}")
            removeClient(selectedId)
        } catch (e: Exception) {
            Log.e(TAG, "PCM 发送异常", e)
        }
    }

    private fun broadcastState() {
        val state = stateProvider()
        val selectedId = _selectedClientId
        val json = JSONObject().apply {
            put("type", "state_update")
            put("is_playing", state.isPlaying)
            put("position_ms", state.positionMs)
            put("duration_ms", state.durationMs)
            put("title", state.title)
            put("audio_output", if (selectedId != null) "phone" else "speaker")
            put("selected_device", selectedId ?: JSONObject.NULL)
        }

        clients.values.toList().forEach { conn ->
            try {
                sendJson(conn.output, json, FRAME_STATE_SYNC)
            } catch (e: Exception) {
                Log.w(TAG, "状态广播失败: ${conn.client.name}")
            }
        }
    }

    private fun sendHeartbeats() {
        clients.values.toList().forEach { conn ->
            try {
                synchronized(conn.output) {
                    conn.output.write(FRAME_HEARTBEAT.toInt())
                    conn.output.flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "心跳发送失败: ${conn.client.name}")
            }
        }
    }

    private fun sendOutputChanged(output: OutputStream, outputTarget: String) {
        try {
            val json = JSONObject().apply {
                put("type", "audio_output_changed")
                put("output", outputTarget)
            }
            sendJson(output, json, FRAME_OUTPUT_CHANGED)
        } catch (e: Exception) {
            Log.e(TAG, "发送输出变化通知失败", e)
        }
    }

    private fun sendJson(output: OutputStream, json: JSONObject, frameType: Byte = FRAME_STATE_SYNC) {
        synchronized(output) {
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            output.write(frameType.toInt())
            val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            lenBuf.putInt(bytes.size)
            output.write(lenBuf.array())
            output.write(bytes)
            output.flush()
        }
    }

    private suspend fun readJson(stream: InputStream, timeoutMs: Long): JSONObject? {
        return withTimeoutOrNull(timeoutMs) {
            // 简单实现：读取帧类型 + 长度 + JSON
            val frameType = stream.read()
            if (frameType != FRAME_CONTROL_COMMAND.toInt() && frameType != 0) {
                // 尝试直接读取 JSON（握手阶段可能没有帧类型前缀）
                return@withTimeoutOrNull null
            }

            val lenBytes = ByteArray(4)
            stream.read(lenBytes)
            val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int

            if (len <= 0 || len > 65536) return@withTimeoutOrNull null

            val jsonBytes = ByteArray(len)
            stream.read(jsonBytes)
            JSONObject(String(jsonBytes, Charsets.UTF_8))
        }
    }

    private fun readJsonFrame(stream: InputStream): JSONObject? {
        val lenBytes = ByteArray(4)
        val read = stream.read(lenBytes)
        if (read < 4) return null

        val len = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int
        if (len <= 0 || len > 65536) return null

        val jsonBytes = ByteArray(len)
        stream.read(jsonBytes)
        return JSONObject(String(jsonBytes, Charsets.UTF_8))
    }

    private fun removeClient(clientId: String) {
        val conn = clients.remove(clientId) ?: return
        conn.isActive = false
        conn.receiveJob?.cancel()

        try {
            conn.client.socket.close()
        } catch (_: Exception) {}

        // 如果移除的是选中设备，回退到扬声器
        if (_selectedClientId == clientId) {
            _selectedClientId = null
            selectedClientId = null
            Log.i(TAG, "选中设备断开，回退到扬声器")
        }

        updateClientList()
        Log.i(TAG, "客户端已移除: ${conn.client.name} ($clientId)")
    }

    private fun updateClientList() {
        val list = clients.values.map { it.client }.toList()
        _connectedClients.value = list
        onClientListChanged?.invoke(list)
    }

    fun isRunning(): Boolean = serverSocket?.isBound == true
}
