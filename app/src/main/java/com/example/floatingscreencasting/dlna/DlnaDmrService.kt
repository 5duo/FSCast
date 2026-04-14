package com.example.floatingscreencasting.dlna

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * DLNA DMR服务
 * 管理SSDP和HTTP服务器，提供DLNA投屏功能
 */
class DlnaDmrService(private val context: Context) {

    companion object {
        private const val TAG = "DlnaDmrService"
        @Volatile
        private var instance: DlnaDmrService? = null

        fun getInstance(context: Context): DlnaDmrService {
            return instance ?: synchronized(this) {
                instance ?: DlnaDmrService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val ssdpServer = SsdpServer(context)
    private val httpServer = DlnaHttpServer()

    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 投屏状态回调
    var onCastingStateChanged: ((isCasting: Boolean, title: String?) -> Unit)? = null
    var onErrorOccurred: ((error: String) -> Unit)? = null

    /**
     * 启动DLNA服务
     */
    suspend fun start(): Boolean {
        if (isRunning) {
            Log.w(TAG, "DLNA服务已经在运行")
            return true
        }

        Log.d(TAG, "正在启动DLNA服务...")

        // 启动HTTP服务器
        try {
            httpServer.start()
            Log.d(TAG, "HTTP服务器已启动，端口: 7676")
        } catch (e: Exception) {
            Log.e(TAG, "启动HTTP服务器失败", e)
            onErrorOccurred?.invoke("启动HTTP服务器失败: ${e.message}")
            return false
        }

        // 设置控制命令回调
        setupHttpCallbacks()

        // 启动SSDP服务器
        val ssdpStarted = ssdpServer.start()
        if (!ssdpStarted) {
            Log.e(TAG, "启动SSDP服务器失败")
            onErrorOccurred?.invoke("启动SSDP服务器失败")
            // HTTP服务器已启动，但SSDP失败，仍然继续
        }

        isRunning = true
        Log.d(TAG, "DLNA服务启动成功")
        onCastingStateChanged?.invoke(false, null)

        return true
    }

    /**
     * 停止DLNA服务
     */
    suspend fun stop() {
        if (!isRunning) {
            return
        }

        Log.d(TAG, "正在停止DLNA服务...")

        ssdpServer.stop()

        try {
            httpServer.stop()
        } catch (e: Exception) {
            // 忽略停止错误
        }

        isRunning = false
        Log.d(TAG, "DLNA服务已停止")
        onCastingStateChanged?.invoke(false, null)
    }

    /**
     * 检查服务是否正在运行
     */
    fun isActive(): Boolean = isRunning

    /**
     * 更新传输状态（供VideoPresentation调用）
     */
    fun updateTransportState(state: String) {
        httpServer.updateTransportState(state)
    }

    /**
     * 设置HTTP服务器回调
     */
    private fun setupHttpCallbacks() {
        httpServer.apply {
            setPlayCommand { uri ->
                serviceScope.launch {
                    handlePlayCommand(uri)
                }
            }

            setStopCommand {
                serviceScope.launch {
                    handleStopCommand()
                }
            }

            setPauseCommand {
                serviceScope.launch {
                    handlePauseCommand()
                }
            }

            setSeekCommand { target ->
                serviceScope.launch {
                    handleSeekCommand(target)
                }
            }

            onGetDuration = {
                // 从外部获取视频时长
                onGetDuration?.invoke() ?: 0L
            }

            onGetPosition = {
                // 从外部获取当前播放位置
                onGetPosition?.invoke() ?: 0L
            }
        }
    }

    /**
     * 处理播放命令
     */
    private suspend fun handlePlayCommand(uri: String) {
        Log.d(TAG, "处理播放命令: $uri")

        if (uri.isBlank()) {
            // 恢复播放
            onCastingStateChanged?.invoke(true, "投屏中")
            return
        }

        // 提取视频标题（从元数据或URL中）
        val title = extractTitleFromUri(uri)

        try {
            // 检查onPlayMedia回调是否设置
            Log.d(TAG, "onPlayMedia回调是否为null: ${onPlayMedia == null}")

            // 通过EventBus或回调通知Presentation播放视频
            onPlayMedia?.invoke(uri)
            Log.d(TAG, "onPlayMedia回调已调用")

            onCastingStateChanged?.invoke(true, title)
        } catch (e: Exception) {
            Log.e(TAG, "播放失败", e)
            onErrorOccurred?.invoke("播放失败: ${e.message}")
        }
    }

    /**
     * 处理停止命令
     */
    private suspend fun handleStopCommand() {
        Log.d(TAG, "处理停止命令")
        onStopMedia?.invoke()
        onCastingStateChanged?.invoke(false, null)
    }

    /**
     * 处理暂停命令
     */
    private suspend fun handlePauseCommand() {
        Log.d(TAG, "处理暂停命令")
        onPauseMedia?.invoke()
    }

    /**
     * 处理Seek命令
     */
    private suspend fun handleSeekCommand(target: String) {
        Log.d(TAG, "处理Seek命令: $target")
        onSeekMedia?.invoke(target)
    }

    /**
     * 从URI中提取标题
     */
    private fun extractTitleFromUri(uri: String): String {
        return when {
            uri.contains("bilibili") -> "哔哩哔哩"
            uri.contains("iqiyi") -> "爱奇艺"
            uri.contains("v.qq.com") -> "腾讯视频"
            uri.contains("youku") -> "优酷"
            else -> "在线视频"
        }
    }

    // 媒体控制回调
    var onPlayMedia: ((String) -> Unit)? = null
    var onStopMedia: (() -> Unit)? = null
    var onPauseMedia: (() -> Unit)? = null
    var onSeekMedia: ((String) -> Unit)? = null
    var onGetDuration: (() -> Long)? = null
    var onGetPosition: (() -> Long)? = null
}
