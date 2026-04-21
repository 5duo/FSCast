package com.example.floatingscreencasting.data.remote.dlna

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.floatingscreencasting.data.remote.http.DlnaHttpServer
import com.example.floatingscreencasting.data.remote.http.DlnaMediaMetadata
import com.example.floatingscreencasting.data.remote.discovery.SsdpServer
import com.example.floatingscreencasting.utils.UriUtils
import kotlinx.coroutines.*

/**
 * 投屏请求数据类
 * 保存DLNA投屏的URL和元数据，用于同步到手机端
 */
data class CastingRequest(
    val uri: String,              // 视频URL
    val metadata: String = "",    // 元数据
    val httpHeaders: Map<String, String> = emptyMap(),  // HTTP头信息
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DLNA DMR服务
 * 管理SSDP和HTTP服务器，提供DLNA投屏功能
 */
class DlnaRendererService(private val context: Context) {

    companion object {
        private const val TAG = "DlnaRendererService"
        @Volatile
        private var instance: DlnaRendererService? = null

        fun getInstance(context: Context): DlnaRendererService {
            return instance ?: synchronized(this) {
                instance ?: DlnaRendererService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val ssdpServer = SsdpServer(context)
    private val httpServer = DlnaHttpServer()

    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 保存当前投屏请求，用于同步到手机端
    @Volatile
    private var currentCastingRequest: CastingRequest? = null

    // 主线程Handler，用于在主线程上访问ExoPlayer
    private val mainHandler = Handler(Looper.getMainLooper())

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

        Log.d(TAG, "========== 正在启动DLNA服务... ==========")

        // 设置控制命令回调
        setupHttpCallbacks()
        Log.d(TAG, "HTTP回调已设置")

        // 启动HTTP服务器
        try {
            Log.d(TAG, "准备调用httpServer.start()...")
            httpServer.start()
            Log.d(TAG, "httpServer.start()调用完成")
        } catch (e: Exception) {
            Log.e(TAG, "启动HTTP服务器失败", e)
            onErrorOccurred?.invoke("启动HTTP服务器失败: ${e.message}")
            return false
        }

        // 启动SSDP服务器（异步，不等待完成）
        Log.d(TAG, "准备启动SSDP服务器（异步）...")
        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "SSDP服务器异步启动开始...")
                val result = ssdpServer.start()
                Log.d(TAG, "SSDP服务器异步启动完成，result=$result")
                if (!result) {
                    Log.e(TAG, "启动SSDP服务器失败")
                    onErrorOccurred?.invoke("启动SSDP服务器失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动SSDP服务器时发生异常", e)
                onErrorOccurred?.invoke("启动SSDP服务器异常: ${e.message}")
            }
        }
        Log.d(TAG, "SSDP服务器异步启动已提交")

        Log.d(TAG, "即将设置isRunning=true")
        isRunning = true
        Log.d(TAG, "========== DLNA服务启动成功 ==========")
        onCastingStateChanged?.invoke(false, null)

        Log.d(TAG, "准备返回true")
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

        // 🔧 修复内存泄漏：取消serviceScope
        serviceScope.cancel()

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
     * 设置播放状态回调（供MainActivity调用）
     */
    fun setPlaybackCallbacks(
        onDuration: () -> Long,
        onPosition: () -> Long
    ) {
        Log.d(TAG, "========== setPlaybackCallbacks调用 ==========")
        this.onGetDuration = onDuration
        this.onGetPosition = onPosition
        // 更新HTTP服务器的回调 - 使用Handler在主线程上访问ExoPlayer
        httpServer.setGetDurationCallback {
            // 使用Handler在主线程上获取duration
            var durationResult = 0L
            val lock = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                try {
                    durationResult = getDuration()
                } catch (e: Exception) {
                    Log.e(TAG, "获取duration失败", e)
                }
                lock.countDown()
            }
            lock.await(1, java.util.concurrent.TimeUnit.SECONDS) // 等待最多1秒
            Log.d(TAG, "getDuration回调被调用，返回: ${durationResult}s")
            durationResult
        }
        httpServer.setGetPositionCallback {
            // 使用Handler在主线程上获取position
            var positionResult = 0L
            val lock = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                try {
                    positionResult = getPosition()
                } catch (e: Exception) {
                    Log.e(TAG, "获取position失败", e)
                }
                lock.countDown()
            }
            lock.await(1, java.util.concurrent.TimeUnit.SECONDS) // 等待最多1秒
            Log.d(TAG, "getPosition回调被调用，返回: ${positionResult}s")
            positionResult
        }
        Log.d(TAG, "========== setPlaybackCallbacks完成 ==========")
    }

    /**
     * 更新传输状态（供VideoPresentation调用）
     */
    fun updateTransportState(state: String) {
        httpServer.updateTransportState(state)
    }

    /**
     * 获取HTTP服务器实例（用于发送DLNA事件通知）
     */
    fun getHttpServer(): DlnaHttpServer {
        return httpServer
    }

    /**
     * 设置是否拦截Stop命令（用于原源模式）
     * @param intercept true=拦截Stop命令并忽略，false=正常处理Stop命令
     */
    fun setStopCommandIntercept(intercept: Boolean) {
        httpServer.setStopCommandIntercept(intercept)
    }

    /**
     * 设置Stop命令被拦截时的回调（用于暂停2秒后恢复播放）
     */
    fun setOnStopCommandIntercepted(callback: () -> Unit) {
        httpServer.setOnStopCommandIntercepted(callback)
    }

    /**
     * 设置HTTP服务器回调
     */
    private fun setupHttpCallbacks() {
        httpServer.setPlayCommand { mediaMetadata ->
            serviceScope.launch {
                handlePlayCommand(mediaMetadata)
            }
        }

        httpServer.setStopCommand {
            serviceScope.launch {
                handleStopCommand()
            }
        }

        httpServer.setPauseCommand {
            serviceScope.launch {
                handlePauseCommand()
            }
        }

        httpServer.setSeekCommand { target ->
            serviceScope.launch {
                handleSeekCommand(target)
            }
        }
    }

    /**
     * 获取视频时长（供HTTP服务器调用）
     */
    private fun getDuration(): Long {
        return onGetDuration?.invoke() ?: 0L
    }

    /**
     * 获取播放位置（供HTTP服务器调用）
     */
    private fun getPosition(): Long {
        return onGetPosition?.invoke() ?: 0L
    }

    /**
     * 处理播放命令
     */
    private suspend fun handlePlayCommand(mediaMetadata: DlnaMediaMetadata) {
        val uri = mediaMetadata.uri
        val httpHeaders = mediaMetadata.httpHeaders

        Log.d(TAG, "处理播放命令: $uri")
        Log.d(TAG, "视频标题: ${mediaMetadata.title}")
        Log.d(TAG, "视频时长: ${mediaMetadata.durationMs}ms")
        Log.d(TAG, "HTTP头: $httpHeaders")

        if (uri.isBlank()) {
            // 恢复播放（暂停后再播放的情况）
            httpServer.updateTransportState("PLAYING")
            onCastingStateChanged?.invoke(true, "投屏中")
            onPlay?.invoke()  // 调用play恢复播放
            return
        }

        // 保存投屏请求（用于同步到手机端）
        // 处理B站App的占位符标题格式：空、"studio_video_xxx"、"video"
        val savedTitle = if (mediaMetadata.title.isBlank() ||
                            mediaMetadata.title == "video" ||
                            mediaMetadata.title.startsWith("studio_video_") ||
                            mediaMetadata.title.matches(Regex("studio_video_\\d+"))) {
            UriUtils.extractTitleFromUri(uri)
        } else {
            mediaMetadata.title
        }
        currentCastingRequest = CastingRequest(
            uri = uri,
            metadata = savedTitle,
            httpHeaders = httpHeaders
        )
        Log.d(TAG, "投屏请求已保存: $uri, 标题: $savedTitle")

        try {
            // 检查onPlayMedia回调是否设置
            Log.d(TAG, "onPlayMedia回调是否为null: ${onPlayMedia == null}")

            // 更新传输状态为PLAYING
            httpServer.updateTransportState("PLAYING")

            // 通过EventBus或回调通知Presentation播放视频
            // 传递标题和时长信息
            // 处理B站App的占位符标题格式：空、"studio_video_xxx"、"video"
            val displayTitle = if (mediaMetadata.title.isBlank() ||
                                  mediaMetadata.title == "video" ||
                                  mediaMetadata.title.startsWith("studio_video_") ||
                                  mediaMetadata.title.matches(Regex("studio_video_\\d+"))) {
                UriUtils.extractTitleFromUri(uri)
            } else {
                mediaMetadata.title
            }

            onPlayMediaWithMetadata?.invoke(uri, httpHeaders, displayTitle, mediaMetadata.durationMs, mediaMetadata.initialPositionMs)
                ?: onPlayMedia?.invoke(uri, httpHeaders)

            Log.d(TAG, "onPlayMedia回调已调用，标题: $displayTitle")

            // 使用metadata中的标题，如果没有或是占位符格式则从URL提取
            // B站App有时会发送"studio_video_xxx"或"video"格式的占位符标题
            val title = if (mediaMetadata.title.isBlank() ||
                           mediaMetadata.title == "video" ||
                           mediaMetadata.title.startsWith("studio_video_") ||
                           mediaMetadata.title.matches(Regex("studio_video_\\d+"))) {
                UriUtils.extractTitleFromUri(uri)
            } else {
                mediaMetadata.title
            }
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
        httpServer.updateTransportState("PAUSED_PLAYBACK")
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
     * 获取当前投屏请求
     * 用于同步到手机端
     */
    fun getCurrentCastingRequest(): CastingRequest? = currentCastingRequest

    /**
     * 清除当前投屏请求
     */
    fun clearCastingRequest() {
        currentCastingRequest = null
    }

    // 媒体控制回调
    var onPlayMedia: ((String, Map<String, String>) -> Unit)? = null
    var onPlayMediaWithMetadata: ((String, Map<String, String>, String, Long, Long) -> Unit)? = null
    var onStopMedia: (() -> Unit)? = null
    var onPauseMedia: (() -> Unit)? = null
    var onPlay: (() -> Unit)? = null
    var onSeekMedia: ((String) -> Unit)? = null
    var onGetDuration: (() -> Long)? = null
    var onGetPosition: (() -> Long)? = null
}

// 向后兼容的类型别名
@Deprecated("使用 DlnaRendererService 代替", ReplaceWith("DlnaRendererService"))
typealias DlnaDmrService = DlnaRendererService
