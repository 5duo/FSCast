package com.example.floatingscreencasting.dlna

import android.util.Log
import com.example.floatingscreencasting.dlna.DlnaDmcClient.DlnaDevice
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
    private val dlnaDmcClient: DlnaDmcClient,
    private val phoneDeviceManager: PhoneDeviceManager
) {

    companion object {
        private const val TAG = "AudioOutputController"
        private const val SYNC_INTERVAL_MS = 500L // 进度同步间隔
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
                // 切换到手机端
                val device = phoneDeviceManager.getSelectedDevice()
                if (device == null) {
                    Log.w(TAG, "没有选中的手机设备")
                    return false
                }

                currentVideoUri = if (videoUri.isNotEmpty()) videoUri else currentVideoUri

                // 将视频推送到手机端（传递HTTP头）
                val success = if (currentVideoUri.isNotEmpty()) {
                    dlnaDmcClient.setAvTransportUri(device, currentVideoUri, currentHttpHeaders) &&
                            dlnaDmcClient.play(device)
                } else {
                    dlnaDmcClient.play(device)
                }

                if (success) {
                    currentMode = mode
                    startProgressSync()
                    // 静音车机端
                    muteControlCallback?.setMuted(true)
                    Log.i(TAG, "手机端模式：车机静音")
                }

                return success
            }
        }
    }

    /**
     * 设置当前视频URI
     */
    fun setCurrentVideoUri(uri: String, httpHeaders: Map<String, String> = emptyMap()) {
        currentVideoUri = uri
        currentHttpHeaders = httpHeaders
        Log.d(TAG, "设置当前视频URI: ${uri.take(50)}...")
        Log.d(TAG, "HTTP头: $httpHeaders")
    }

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
     * 同步车机进度到手机
     * 当车机端进度更新时调用
     */
    suspend fun syncProgress(positionMs: Long): Boolean {
        if (currentMode != OutputMode.PHONE) {
            return true // 车机模式不需要同步
        }

        val device = phoneDeviceManager.getSelectedDevice()
        if (device == null) {
            return false
        }

        // 发送Seek命令到手机端
        return dlnaDmcClient.seek(device, positionMs)
    }

    /**
     * 开始进度同步
     * 定期将车机进度同步到手机
     */
    private fun startProgressSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (currentMode == OutputMode.PHONE) {
                delay(SYNC_INTERVAL_MS)
                // 进度同步逻辑由外部调用syncProgress实现
            }
        }
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
