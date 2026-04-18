package com.example.floatingscreencasting.dlna

import android.util.Log
import com.example.floatingscreencasting.data.remote.dlna.DlnaControlPoint
import com.example.floatingscreencasting.data.remote.dlna.DlnaControlPoint.DlnaDevice
import com.example.floatingscreencasting.websocket.CarWebSocketServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音频输出控制器
 * 协调车机和手机端的播放状态
 */
class AudioOutputController(
    private val dlnaDmcClient: DlnaControlPoint,
    private val phoneDeviceManager: PhoneDeviceManager,
    webSocketServer: CarWebSocketServer? = null
) {

    companion object {
        private const val TAG = "AudioOutputController"
        private const val PROGRESS_CHECK_INTERVAL_MS = 5000L // 进度检查间隔：5秒（优化：减少同步延迟）
        private const val SYNC_THRESHOLD_MS = 3000L // 同步阈值：3秒（优化：减少不必要的重新对齐，避免卡顿）
    }

    enum class OutputMode {
        SPEAKER,  // 车机扬声器
        PHONE     // 手机端
    }

    private var currentMode = OutputMode.SPEAKER
    private var currentVideoUri: String = ""
    private var currentHttpHeaders: Map<String, String> = emptyMap()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null

    // WebSocket服务器引用（可更新，用于服务器重启场景）
    private var webSocketServer: CarWebSocketServer? = webSocketServer

    // 命令循环避免：缓存已处理的命令ID（10秒TTL）
    private val processedCommands = mutableMapOf<String, Long>()
    private val commandCleanupJob = scope.launch {
        while (isActive) {
            delay(10000) // 每10秒清理一次过期缓存
            val now = System.currentTimeMillis()
            processedCommands.entries.removeIf { (_, timestamp) -> now - timestamp > 10000 }
        }
    }

    // 播放状态监听器
    interface PlaybackStateListener {
        fun onPlay()
        fun onPause()
        fun onSeek(positionMs: Long)
        fun onStop()
        fun getCurrentPosition(): Long  // 获取当前播放位置
    }

    /**
     * 静音控制回调
     * 用于通知车机端视频静音/取消静音
     */
    interface MuteControlCallback {
        fun setMuted(muted: Boolean)
    }

    private var playbackStateListener: PlaybackStateListener? = null
    private var muteControlCallback: MuteControlCallback? = null

    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: PlaybackStateListener) {
        this.playbackStateListener = listener
    }

    /**
     * 设置静音控制回调
     */
    fun setMuteControlCallback(callback: MuteControlCallback) {
        this.muteControlCallback = callback
    }

    /**
     * 切换音频输出模式
     * 只使用WebSocket通信，不再使用DLNA降级
     */
    suspend fun switchOutputMode(mode: OutputMode, videoUri: String = ""): Boolean {
        Log.i(TAG, "切换音频输出: $currentMode -> $mode")

        when (mode) {
            OutputMode.SPEAKER -> {
                // 切换到车机扬声器
                stopPhonePlayback()
                currentMode = mode
                // 取消静音
                muteControlCallback?.setMuted(false)
                Log.i(TAG, "车机扬声器模式：取消静音")
                return true
            }
            OutputMode.PHONE -> {
                // 只使用WebSocket通信
                Log.i(TAG, "========================================")
                Log.i(TAG, "切换到手机模式，检查WebSocket连接状态...")
                val server = webSocketServer
                Log.i(TAG, "webSocketServer是否为null: ${server == null}")
                if (server != null) {
                    Log.i(TAG, "已连接客户端数量: ${server.getClientCount()}")
                    Log.i(TAG, "已连接客户端列表: ${server.getConnectedClients()}")
                }
                Log.i(TAG, "当前视频URI: ${currentVideoUri.take(50)}...")
                Log.i(TAG, "当前视频URI长度: ${currentVideoUri.length}")
                Log.i(TAG, "HTTP头数量: ${currentHttpHeaders.size}")
                Log.i(TAG, "========================================")

                if (server == null) {
                    Log.e(TAG, "WebSocket服务器未初始化")
                    return false
                }

                if (!server.hasConnectedClients()) {
                    Log.w(TAG, "没有WebSocket客户端连接")
                    return false
                }

                currentVideoUri = if (videoUri.isNotEmpty()) videoUri else currentVideoUri
                Log.i(TAG, "使用的视频URI: ${currentVideoUri.take(50)}..., 长度: ${currentVideoUri.length}")

                if (currentVideoUri.isNotEmpty()) {
                    // 新的同步方案：
                    // 1. 车机先暂停
                    Log.i(TAG, "步骤1: 车机暂停播放")
                    playbackStateListener?.onPause()

                    // 2. 发送播放命令+当前进度给手机
                    // 从监听器获取当前播放进度
                    val currentPositionMs = playbackStateListener?.getCurrentPosition() ?: 0L
                    Log.i(TAG, "步骤2: 发送播放命令到手机端，进度: ${currentPositionMs}ms")
                    Log.i(TAG, "视频URI: ${currentVideoUri.take(100)}...")
                    Log.i(TAG, "HTTP头数量: ${currentHttpHeaders.size}")

                    val sentCount = server.sendPlayCommandWithPosition(
                        currentVideoUri,
                        currentHttpHeaders,
                        currentPositionMs
                    )

                    Log.i(TAG, "发送结果: $sentCount 个客户端收到命令")

                    if (sentCount == 0) {
                        Log.e(TAG, "WebSocket发送播放命令失败：没有客户端收到命令")
                        return false
                    }

                    // 等待手机端加载视频（3秒，考虑网络延迟）
                    Log.i(TAG, "步骤3: 等待手机端加载视频...")
                    delay(3000)

                    // 4. 同时启动两端播放
                    Log.i(TAG, "步骤4: 同时启动两端播放")
                    // 车机端开始播放（静音状态）
                    playbackStateListener?.onPlay()
                    // 同时发送命令到手机端
                    server.sendResumeCommand()
                    Log.i(TAG, "两端同步启动完成")
                } else {
                    Log.e(TAG, "========================================")
                    Log.e(TAG, "错误：当前没有视频URI，无法切换到手机模式")
                    Log.e(TAG, "请先投屏视频到车机，然后再切换音频输出")
                    Log.e(TAG, "========================================")
                    return false
                }

                // 切换成功
                currentMode = mode
                startProgressSync()
                // 静音车机端
                muteControlCallback?.setMuted(true)
                Log.i(TAG, "手机端模式（WebSocket）：车机静音，同步启动完成")
                return true
            }
        }
    }

    /**
     * 设置当前视频URI
     */
    fun setCurrentVideoUri(uri: String, httpHeaders: Map<String, String> = emptyMap()) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "setCurrentVideoUri被调用")
        Log.i(TAG, "URI长度: ${uri.length}")
        Log.i(TAG, "URI内容: ${uri.take(100)}...")
        Log.i(TAG, "HTTP头数量: ${httpHeaders.size}")
        currentVideoUri = uri
        currentHttpHeaders = httpHeaders
        Log.i(TAG, "URI已保存到currentVideoUri")
        Log.i(TAG, "========================================")
    }

    /**
     * 获取当前视频URI（用于调试）
     */
    fun getCurrentVideoUri(): String = currentVideoUri

    /**
     * 获取当前输出模式
     */
    fun getCurrentMode(): OutputMode {
        return currentMode
    }

    /**
     * 播放
     * @param phonePositionMs 手机端跳转位置（可选）
     * @param syncId 命令唯一ID，用于避免循环（null表示UI触发，非null表示远程命令）
     */
    suspend fun play(phonePositionMs: Long = 0, syncId: String? = null): Boolean {
        Log.d(TAG, "play调用: phonePositionMs=$phonePositionMs, syncId=$syncId, mode=$currentMode")

        return when (currentMode) {
            OutputMode.SPEAKER -> {
                playbackStateListener?.onPlay()
                true
            }
            OutputMode.PHONE -> {
                // 优先使用WebSocket通信
                val server = webSocketServer
                if (server != null && server.hasConnectedClients()) {
                    // 如果需要跳转，先跳转再播放
                    if (phonePositionMs > 0) {
                        server.sendSeekCommand(phonePositionMs)
                    }

                    val sentCount = server.sendPlayCommand(currentVideoUri, currentHttpHeaders)
                    if (sentCount > 0) {
                        startProgressSync()
                        return true
                    }
                }

                // 降级到DLNA方式
                val device = phoneDeviceManager.getSelectedDevice()
                if (device == null) {
                    Log.w(TAG, "没有选中的手机设备")
                    return false
                }

                // 如果手机端需要跳转，先跳转再播放
                var success = true
                if (phonePositionMs > 0) {
                    success = dlnaDmcClient.seek(device, phonePositionMs)
                }

                if (success) {
                    success = dlnaDmcClient.play(device)
                    if (success) {
                        startProgressSync()
                    }
                }

                success
            }
        }
    }

    /**
     * 暂停
     * @param syncId 命令唯一ID，用于避免循环（null表示UI触发，非null表示远程命令）
     */
    suspend fun pause(syncId: String? = null): Boolean {
        Log.d(TAG, "pause调用: syncId=$syncId, mode=$currentMode")

        return when (currentMode) {
            OutputMode.SPEAKER -> {
                playbackStateListener?.onPause()
                true
            }
            OutputMode.PHONE -> {
                // 优先使用WebSocket通信
                val server = webSocketServer
                if (server != null && server.hasConnectedClients()) {
                    val sentCount = server.sendPauseCommand()
                    if (sentCount > 0) {
                        stopProgressSync()
                        return true
                    }
                }

                // 降级到DLNA方式
                val device = phoneDeviceManager.getSelectedDevice()
                if (device == null) {
                    Log.w(TAG, "没有选中的手机设备")
                    return false
                }

                val success = dlnaDmcClient.pause(device)
                if (success) {
                    stopProgressSync()
                }

                success
            }
        }
    }

    /**
     * 停止
     * @param syncId 命令唯一ID，用于避免循环（null表示UI触发，非null表示远程命令）
     */
    suspend fun stop(syncId: String? = null): Boolean {
        Log.d(TAG, "stop调用: syncId=$syncId, mode=$currentMode")
        stopProgressSync()

        return when (currentMode) {
            OutputMode.SPEAKER -> {
                playbackStateListener?.onStop()
                true
            }
            OutputMode.PHONE -> {
                // 优先使用WebSocket通信
                val server = webSocketServer
                if (server != null && server.hasConnectedClients()) {
                    val sentCount = server.sendStopCommand()
                    return sentCount > 0
                }

                // 降级到DLNA方式
                val device = phoneDeviceManager.getSelectedDevice()
                if (device == null) {
                    Log.w(TAG, "没有选中的手机设备")
                    return false
                }

                val success = dlnaDmcClient.stop(device)
                if (success) {
                    stopProgressSync()
                }

                success
            }
        }
    }

    /**
     * 跳转
     * @param positionMs 跳转位置（毫秒）
     * @param syncId 命令唯一ID，用于避免循环（null表示UI触发，非null表示远程命令）
     */
    suspend fun seek(positionMs: Long, syncId: String? = null): Boolean {
        Log.d(TAG, "seek调用: positionMs=$positionMs, syncId=$syncId, mode=$currentMode")

        return when (currentMode) {
            OutputMode.SPEAKER -> {
                playbackStateListener?.onSeek(positionMs)
                true
            }
            OutputMode.PHONE -> {
                // 优先使用WebSocket通信
                val server = webSocketServer
                if (server != null && server.hasConnectedClients()) {
                    val sentCount = server.sendSeekCommand(positionMs)
                    return sentCount > 0
                }

                // 降级到DLNA方式
                val device = phoneDeviceManager.getSelectedDevice()
                if (device == null) {
                    Log.w(TAG, "没有选中的手机设备")
                    return false
                }

                dlnaDmcClient.seek(device, positionMs)
            }
        }
    }


    /**
     * 开始进度同步
     * 每10秒检查一次进度，发送给手机端
     * 手机端收到后会检查差异，如果超过2秒则重新对齐
     * 注意：只要手机端连接就发送进度，无论当前音频输出模式
     */
    private fun startProgressSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (true) {
                val server = webSocketServer
                if (server == null || !server.hasConnectedClients()) {
                    Log.d(TAG, "进度同步: 无手机端连接，停止同步")
                    break
                }

                delay(PROGRESS_CHECK_INTERVAL_MS) // 每5秒检查一次

                // 获取车机端当前播放进度
                val currentPosition = playbackStateListener?.getCurrentPosition() ?: 0L
                Log.d(TAG, "进度同步定时器: mode=$currentMode, position=${currentPosition}ms")

                // 发送给手机端
                if (server != null && server.hasConnectedClients()) {
                    Log.d(TAG, "进度检查: 发送车机进度 ${currentPosition}ms 到手机端")
                    server.sendProgressUpdate(
                        currentPosition,
                        0,  // duration不需要（手机端从视频流获取）
                        true  // isPlaying不需要精确判断
                    )
                } else {
                    Log.d(TAG, "进度同步: 无手机端连接，停止同步")
                    break
                }
            }
        }
        Log.i(TAG, "进度同步定时器已启动")
    }

    /**
     * 停止进度同步
     */
    private fun stopProgressSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * 发送初始进度信息给手机端
     * 在投屏开始时调用
     */
    private fun sendInitialProgress() {
        val server = webSocketServer
        if (server != null && server.hasConnectedClients()) {
            val currentPosition = playbackStateListener?.getCurrentPosition() ?: 0L
            Log.i(TAG, "发送初始进度: ${currentPosition}ms")
            server.sendProgressUpdate(currentPosition, 0, true)
        }
    }

    /**
     * 停止手机端播放
     */
    private suspend fun stopPhonePlayback() {
        // 使用WebSocket停止手机端播放
        val server = webSocketServer
        if (server != null && server.hasConnectedClients()) {
            Log.i(TAG, "发送stop命令到手机端")
            server.sendStopCommand()
        }
        stopProgressSync()
    }

    /**
     * 执行来自手机端的远程命令
     * @param action 命令类型（play, pause, stop, seek, switch_output）
     * @param data 命令数据（JSON对象）
     * @param syncId 命令唯一ID，用于避免循环
     */
    suspend fun executeRemoteCommand(action: String, data: org.json.JSONObject, syncId: String) {
        // 检查命令是否已处理（避免循环）
        if (isCommandProcessed(syncId)) {
            Log.d(TAG, "忽略重复命令: syncId=$syncId")
            return
        }

        // 标记命令已处理
        markCommandProcessed(syncId)

        Log.i(TAG, "执行远程命令: action=$action, syncId=$syncId, mode=$currentMode")

        when (action) {
            "play" -> {
                // 远程播放命令：在车机端播放，同时同步到手机端
                play(syncId = syncId)
            }
            "pause" -> {
                // 远程暂停命令：暂停车机端，同时同步到手机端
                pause(syncId = syncId)
            }
            "stop" -> {
                // 远程停止命令：停止车机端，同时同步到手机端
                stop(syncId = syncId)
            }
            "seek" -> {
                // 远程跳转命令：跳转车机端进度，同时同步到手机端
                val positionMs = data.optLong("position", 0)
                seek(positionMs, syncId)
            }
            "switch_output" -> {
                // 远程切换音频输出命令
                val modeStr = data.optString("mode", "speaker")
                val newMode = if (modeStr == "phone") OutputMode.PHONE else OutputMode.SPEAKER
                switchOutputMode(newMode)
            }
            else -> {
                Log.w(TAG, "未知的远程命令: $action")
            }
        }
    }

    /**
     * 检查命令是否已处理
     */
    private fun isCommandProcessed(syncId: String): Boolean {
        val timestamp = processedCommands[syncId] ?: return false
        // 检查是否在10秒内
        val age = System.currentTimeMillis() - timestamp
        return age < 10000
    }

    /**
     * 标记命令已处理
     */
    private fun markCommandProcessed(syncId: String) {
        processedCommands[syncId] = System.currentTimeMillis()
        Log.d(TAG, "标记命令已处理: syncId=$syncId")
    }

    /**
     * 释放资源
     */
    fun release() {
        stopProgressSync()
        commandCleanupJob.cancel()
        Log.i(TAG, "AudioOutputController已释放")
    }

    /**
     * 更新WebSocket服务器引用
     * 用于WebSocket服务器重启后更新引用，而不是重新创建整个控制器
     */
    fun updateWebSocketServer(webSocketServer: CarWebSocketServer?) {
        this.webSocketServer = webSocketServer
        Log.i(TAG, "WebSocket服务器引用已更新")
    }
}
