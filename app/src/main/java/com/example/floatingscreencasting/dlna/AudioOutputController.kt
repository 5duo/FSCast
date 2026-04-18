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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音频输出控制器
 * 协调车机和手机端的播放状态
 */
class AudioOutputController(
    private val dlnaDmcClient: DlnaControlPoint,
    private val phoneDeviceManager: PhoneDeviceManager,
    private val webSocketServer: CarWebSocketServer? = null
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
                Log.i(TAG, "webSocketServer是否为null: ${webSocketServer == null}")
                if (webSocketServer != null) {
                    Log.i(TAG, "已连接客户端数量: ${webSocketServer.getClientCount()}")
                    Log.i(TAG, "已连接客户端列表: ${webSocketServer.getConnectedClients()}")
                }
                Log.i(TAG, "当前视频URI: ${currentVideoUri.take(50)}...")
                Log.i(TAG, "当前视频URI长度: ${currentVideoUri.length}")
                Log.i(TAG, "HTTP头数量: ${currentHttpHeaders.size}")
                Log.i(TAG, "========================================")

                if (webSocketServer == null) {
                    Log.e(TAG, "WebSocket服务器未初始化")
                    return false
                }

                if (!webSocketServer.hasConnectedClients()) {
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

                    val sentCount = webSocketServer.sendPlayCommandWithPosition(
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
                    webSocketServer.sendResumeCommand()
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
     */
    suspend fun play(phonePositionMs: Long = 0): Boolean {
        return when (currentMode) {
            OutputMode.SPEAKER -> {
                playbackStateListener?.onPlay()
                true
            }
            OutputMode.PHONE -> {
                // 优先使用WebSocket通信
                if (webSocketServer != null && webSocketServer.hasConnectedClients()) {
                    // 如果需要跳转，先跳转再播放
                    if (phonePositionMs > 0) {
                        webSocketServer.sendSeekCommand(phonePositionMs)
                    }

                    val sentCount = webSocketServer.sendPlayCommand(currentVideoUri, currentHttpHeaders)
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
     */
    suspend fun pause(): Boolean {
        return when (currentMode) {
            OutputMode.SPEAKER -> {
                playbackStateListener?.onPause()
                true
            }
            OutputMode.PHONE -> {
                // 优先使用WebSocket通信
                if (webSocketServer != null && webSocketServer.hasConnectedClients()) {
                    val sentCount = webSocketServer.sendPauseCommand()
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
     */
    suspend fun stop(): Boolean {
        stopProgressSync()

        return when (currentMode) {
            OutputMode.SPEAKER -> {
                playbackStateListener?.onStop()
                true
            }
            OutputMode.PHONE -> {
                // 优先使用WebSocket通信
                if (webSocketServer != null && webSocketServer.hasConnectedClients()) {
                    val sentCount = webSocketServer.sendStopCommand()
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
     */
    suspend fun seek(positionMs: Long): Boolean {
        return when (currentMode) {
            OutputMode.SPEAKER -> {
                playbackStateListener?.onSeek(positionMs)
                true
            }
            OutputMode.PHONE -> {
                // 优先使用WebSocket通信
                if (webSocketServer != null && webSocketServer.hasConnectedClients()) {
                    val sentCount = webSocketServer.sendSeekCommand(positionMs)
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
     */
    private fun startProgressSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (currentMode == OutputMode.PHONE) {
                delay(PROGRESS_CHECK_INTERVAL_MS) // 每10秒检查一次

                // 获取车机端当前播放进度
                val currentPosition = playbackStateListener?.getCurrentPosition() ?: 0L
                Log.d(TAG, "进度同步定时器: mode=$currentMode, position=${currentPosition}ms")

                // 发送给手机端
                if (webSocketServer != null && webSocketServer.hasConnectedClients()) {
                    Log.d(TAG, "进度检查: 发送车机进度 ${currentPosition}ms 到手机端")
                    webSocketServer.sendProgressUpdate(
                        currentPosition,
                        0,  // duration不需要
                        true  // isPlaying不需要精确判断
                    )
                } else {
                    Log.w(TAG, "进度同步: WebSocket未连接或无客户端")
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
     * 停止手机端播放
     */
    private suspend fun stopPhonePlayback() {
        val device = phoneDeviceManager.getSelectedDevice()
        if (device != null) {
            dlnaDmcClient.stop(device)
        }
        stopProgressSync()
    }

    /**
     * 释放资源
     */
    fun release() {
        stopProgressSync()
        Log.i(TAG, "AudioOutputController已释放")
    }
}
