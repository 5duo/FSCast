package com.example.floatingscreencasting.presentation

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.floatingscreencasting.cache.VideoCacheManager
import com.example.floatingscreencasting.R
import com.example.floatingscreencasting.databinding.PresentationVideoBinding
import com.example.floatingscreencasting.history.PlaybackHistoryManager
import java.net.CookieManager

/**
 * 驾驶屏视频浮窗
 * 使用Presentation API在驾驶屏显示视频内容
 */
class VideoPresentation(
    private val outerContext: Context,
    display: Display
) : Presentation(outerContext, display, R.style.Theme_FloatingScreenCasting) {

    private var _binding: PresentationVideoBinding? = null
    private val binding get() = _binding!!

    private var exoPlayer: ExoPlayer? = null

    // 音频路由管理器
    private var audioRouteManager: com.example.floatingscreencasting.audio.AudioRouteManager? = null

    // 同步服务器（用于与手机端同步播放状态）
    private var syncServer: com.example.floatingscreencasting.dlna.SyncServer? = null

    // 播放历史管理器
    private val historyManager: PlaybackHistoryManager = PlaybackHistoryManager.getInstance(outerContext)

    // 静音状态
    private var isMuted = true

    // 当前播放的URL
    private var currentUri: String = ""

    // 当前播放URL的Referer
    private var currentReferer: String = ""

    // 用户设置的透明度（用于非播放状态）
    private var userAlpha: Float = 1.0f

    // 浮窗参数 - 默认居中显示，大小 480x270 (16:9)
    var windowX: Int = 720  // (1920 - 480) / 2
        set(value) {
            field = value
            updateWindowAttributes()
        }
    var windowY: Int = 220  // 接近居中，且能被 10 整除
        set(value) {
            field = value
            updateWindowAttributes()
        }
    var windowWidth: Int = 480  // 16:9，原来的一半
        set(value) {
            field = value
            updateWindowAttributes()
        }
    var windowHeight: Int = 270  // 16:9，原来的一半
        set(value) {
            field = value
            updateWindowAttributes()
        }

    // 窗口透明度 (0.0 - 1.0)
    var windowAlpha: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            userAlpha = field  // 保存用户设置的透明度
            // 如果正在播放，不改变透明度；否则使用用户设置的透明度
            if (!isPlaying()) {
                updateWindowAlpha()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("VideoPresentation", "========== onCreate开始 ==========")
        android.util.Log.d("VideoPresentation", "displayId: ${display.displayId}")
        android.util.Log.d("VideoPresentation", "display.name: ${display.name}")
        android.util.Log.d("VideoPresentation", "display.flags: ${display.flags}")
        android.util.Log.d("VideoPresentation", "Context: $context")

        _binding = PresentationVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        android.util.Log.d("VideoPresentation", "Binding已创建，root: ${binding.root}")
        android.util.Log.d("VideoPresentation", "PlayerView: ${binding.playerView}")
        android.util.Log.d("VideoPresentation", "等待容器: ${binding.waitingContainer}")

        setupWindow()
        android.util.Log.d("VideoPresentation", "窗口属性已设置")

        // 延迟初始化播放器，确保视图已创建
        window?.decorView?.post {
            android.util.Log.d("VideoPresentation", "视图已附加到窗口，开始初始化播放器")
            // 初始化音频路由管理器
            audioRouteManager = com.example.floatingscreencasting.audio.AudioRouteManager(context)
            android.util.Log.d("VideoPresentation", "音频路由管理器已初始化")
            initializePlayer()
            android.util.Log.d("VideoPresentation", "播放器初始化完成")
        }

        android.util.Log.d("VideoPresentation", "isShowing: $isShowing")

        // 设置关闭监听器
        setOnDismissListener {
            android.util.Log.d("VideoPresentation", "Presentation被关闭")
            audioRouteManager?.release()
            audioRouteManager = null
            releasePlayer()
            _binding = null
        }

        android.util.Log.d("VideoPresentation", "========== onCreate完成 ==========")
    }

    /**
     * 设置浮窗参数
     */
    private fun setupWindow() {
        window?.apply {
            attributes = attributes.apply {
                x = windowX
                y = windowY
                width = windowWidth
                height = windowHeight
                gravity = Gravity.TOP or Gravity.START
            }
            // 驾驶屏不可触摸，允许事件穿透
            // 但需要接收按键事件以支持媒体按键控制
            // 因此只设置FLAG_NOT_TOUCHABLE，不设置FLAG_NOT_FOCUSABLE
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            // 确保窗口可以播放音频
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
    }

    /**
     * 更新窗口属性
     */
    private fun updateWindowAttributes() {
        if (window != null && isShowing) {
            window?.attributes = window?.attributes?.apply {
                x = windowX
                y = windowY
                width = windowWidth
                height = windowHeight
            }
        }
    }

    /**
     * 更新窗口透明度
     */
    private fun updateWindowAlpha() {
        if (window != null && isShowing) {
            window?.attributes = window?.attributes?.apply {
                alpha = windowAlpha
            }
        }
    }

    /**
     * 根据URL获取对应的Referer和请求头
     */
    private fun getHeadersForUrl(url: String): Map<String, String> {
        return when {
            url.contains("bilivideo.com") || url.contains("acgvideo.com") || url.contains("bilibili") -> {
                android.util.Log.d("VideoPresentation", "检测到Bilibili视频，设置乐播/MiTV请求头")
                mapOf(
                    "Referer" to "https://www.bilibili.com/",
                    "Origin" to "https://www.bilibili.com",
                    // 伪装成小米电视/乐播插件的User-Agent
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 9; MiTV) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/74.0.3729.136 Mobile Safari/537.36 BiliApp/1.0 Lelink/1.0",
                    "Accept" to "*/*",
                    "Accept-Language" to "zh-CN,zh;q=0.9",
                    "Connection" to "keep-alive",
                    "X-Lelink-XML" to "1",  // 乐播特征标识
                    "X-Real-IP" to "127.0.0.1"  // 乐播有时会带的字段
                )
            }
            url.contains("iqiyi.com") || url.contains("qiyi.com") -> {
                android.util.Log.d("VideoPresentation", "检测到爱奇艺视频，设置小米电视请求头")
                mapOf(
                    "Referer" to "https://www.iqiyi.com/",
                    "User-Agent" to "MiTV/1.0 (Linux;Android 12) MI_TV_4"
                )
            }
            url.contains("qq.com") -> {
                android.util.Log.d("VideoPresentation", "检测到腾讯视频，设置小米电视请求头")
                mapOf(
                    "Referer" to "https://v.qq.com/",
                    "User-Agent" to "MiTV/1.0 (Linux;Android 12) MI_TV_4"
                )
            }
            url.contains("youku.com") -> {
                android.util.Log.d("VideoPresentation", "检测到优酷视频，设置小米电视请求头")
                mapOf(
                    "Referer" to "https://www.youku.com/",
                    "User-Agent" to "MiTV/1.0 (Linux;Android 12) MI_TV_4"
                )
            }
            else -> {
                android.util.Log.d("VideoPresentation", "未知视频源，使用默认请求头")
                mapOf(
                    "User-Agent" to "MiTV/1.0 (Linux;Android 12) MI_TV_4"
                )
            }
        }
    }

    /**
     * 初始化ExoPlayer
     */
    private fun initializePlayer() {
        android.util.Log.d("VideoPresentation", "========== initializePlayer开始 ==========")

        // 创建HTTP数据源工厂
        val httpHeaders = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
            "Connection" to "keep-alive"
        )

        android.util.Log.d("VideoPresentation", "创建HTTP数据源工厂")

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("MiTV/1.0 (Linux;Android 12) MI_TV_4")
            .setConnectTimeoutMs(30 * 1000)
            .setReadTimeoutMs(30 * 1000)
            .setDefaultRequestProperties(httpHeaders)

        // 使用DefaultDataSource包装，支持HTTP和其他协议
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            httpDataSourceFactory
        )

        // 创建支持HLS的MediaSourceFactory
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        android.util.Log.d("VideoPresentation", "创建ExoPlayer实例")

        // 创建正确的AudioAttributes - 按照爱奇艺视频应用配置
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        android.util.Log.d("VideoPresentation", "AudioAttributes已设置: USAGE_MEDIA, CONTENT_TYPE_MOVIE")
        android.util.Log.d("VideoPresentation", "使用outerContext创建ExoPlayer: $outerContext")

        // 重要：使用outerContext（MainActivity的Context）而不是Presentation的context
        // 这样ExoPlayer可以正确继承Activity的音频配置和路由
        val playerBuilder = ExoPlayer.Builder(outerContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)  // true = handleAudioFocus

        exoPlayer = playerBuilder.build()
            .apply {
                android.util.Log.d("VideoPresentation", "将ExoPlayer绑定到PlayerView")
                binding.playerView.player = this
                android.util.Log.d("VideoPresentation", "PlayerView.player已设置: ${binding.playerView.player != null}")
                android.util.Log.d("VideoPresentation", "PlayerView.width: ${binding.playerView.width}, height: ${binding.playerView.height}")

                // 添加状态监听器
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateName = when (playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN($playbackState)"
                        }
                        android.util.Log.d("VideoPresentation", "播放状态变化: $stateName, isPlaying=$isPlaying, playWhenReady=$playWhenReady")
                        when (playbackState) {
                            Player.STATE_READY -> {
                                // 准备就绪，确保播放
                                if (!playWhenReady) {
                                    android.util.Log.d("VideoPresentation", "状态READY但playWhenReady=false，设置自动播放")
                                    playWhenReady = true
                                }
                                if (!isPlaying) {
                                    android.util.Log.d("VideoPresentation", "状态READY但未播放，尝试播放")
                                    play()
                                }
                            }
                            Player.STATE_ENDED -> {
                                android.util.Log.d("VideoPresentation", "播放结束")
                                // 播放结束，显示等待提示
                                binding.waitingContainer.isVisible = true
                            }
                            Player.STATE_BUFFERING -> {
                                android.util.Log.d("VideoPresentation", "缓冲中...")
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        android.util.Log.d("VideoPresentation", "isPlaying变化: $isPlaying")
                        super.onIsPlayingChanged(isPlaying)
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("VideoPresentation", "播放器错误: ${error.message}", error)
                        android.util.Log.e("VideoPresentation", "错误代码: ${error.errorCode}")
                        android.util.Log.e("VideoPresentation", "错误原因: ${error.errorCodeName}")

                        // 播放失败，恢复用户设置的透明度
                        setWindowAlphaForPlaying(false)
                        // 显示等待提示
                        binding.waitingContainer.isVisible = true

                        // 通知MainActivity播放失败
                        try {
                            val intent = android.content.Intent("com.example.floatingscreencasting.PLAYBACK_ERROR")
                            intent.putExtra("error", error.message ?: "Unknown error")
                            intent.putExtra("errorCode", error.errorCode)
                            context.sendBroadcast(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("VideoPresentation", "发送错误广播失败", e)
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        android.util.Log.d("VideoPresentation", "视频尺寸: ${videoSize.width}x${videoSize.height}")
                    }

                    override fun onRenderedFirstFrame() {
                        android.util.Log.d("VideoPresentation", "首帧已渲染")
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: androidx.media3.common.Player.PositionInfo,
                        newPosition: androidx.media3.common.Player.PositionInfo,
                        reason: Int
                    ) {
                        // 播放位置变化时更新历史记录
                        if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                            updatePlaybackHistory()
                        }
                    }
                })

                // 定期保存播放进度（每10秒）
                Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                    override fun run() {
                        if (isPlaying()) {
                            updatePlaybackHistory()
                        }
                        Handler(Looper.getMainLooper()).postDelayed(this, 10000)
                    }
                }, 10000)
            }

        android.util.Log.d("VideoPresentation", "ExoPlayer创建完成: $exoPlayer")

        // 启动同步服务器（用于与手机端同步播放状态）
        syncServer = com.example.floatingscreencasting.dlna.SyncServer()
        syncServer?.start()
        android.util.Log.d("VideoPresentation", "SyncServer已启动")

        android.util.Log.d("VideoPresentation", "========== initializePlayer完成 ==========")
    }

    /**
     * 播放媒体
     */
    fun playMedia(uri: String) {
        android.util.Log.d("VideoPresentation", "========== playMedia开始 ==========")
        android.util.Log.d("VideoPresentation", "playMedia被调用，uri长度: ${uri.length}")
        android.util.Log.d("VideoPresentation", "当前URI: ${currentUri.take(50)}...")
        android.util.Log.d("VideoPresentation", "新URI: ${uri.take(50)}...")
        android.util.Log.d("VideoPresentation", "isPlaying: ${isPlaying()}")

        // 检查是否是相同的URL（恢复播放）
        if (currentUri == uri && exoPlayer != null) {
            android.util.Log.d("VideoPresentation", "相同URL，尝试恢复播放")
            // 简单切换播放状态
            if (isPlaying()) {
                android.util.Log.d("VideoPresentation", "正在播放，切换为暂停")
                pause()
            } else {
                android.util.Log.d("VideoPresentation", "未播放，切换为播放")
                play()
            }
            return
        }

        android.util.Log.d("VideoPresentation", "实际displayId: ${display.displayId}")
        android.util.Log.d("VideoPresentation", "isShowing: $isShowing")
        android.util.Log.d("VideoPresentation", "window: $window")
        android.util.Log.d("VideoPresentation", "PlayerView: ${binding.playerView}")
        android.util.Log.d("VideoPresentation", "PlayerView.width: ${binding.playerView.width}")
        android.util.Log.d("VideoPresentation", "PlayerView.height: ${binding.playerView.height}")
        android.util.Log.d("VideoPresentation", "PlayerView.visibility: ${binding.playerView.visibility}")
        android.util.Log.d("VideoPresentation", "URI: ${uri.take(100)}...")

        // 如果播放器还没有初始化，等待初始化完成
        if (exoPlayer == null) {
            android.util.Log.w("VideoPresentation", "ExoPlayer还未初始化，等待初始化...")
            Handler(Looper.getMainLooper()).postDelayed({
                if (exoPlayer != null) {
                    android.util.Log.d("VideoPresentation", "ExoPlayer已初始化，继续播放")
                    doPlayMedia(uri)
                } else {
                    android.util.Log.e("VideoPresentation", "ExoPlayer初始化超时！")
                }
            }, 500)
        } else {
            doPlayMedia(uri)
        }
    }

    /**
     * 实际执行播放
     */
    private fun doPlayMedia(uri: String) {
        // 保存当前播放的URL
        currentUri = uri
        android.util.Log.d("VideoPresentation", "doPlayMedia: 开始实际播放")
        android.util.Log.d("VideoPresentation", "完整URL: $uri")

        // 尝试取消A2DP静音（需要root权限）
        audioRouteManager?.setA2DPMuted(false)
        android.util.Log.d("VideoPresentation", "A2DP静音设置已尝试取消")

        // 设置音频路由到蓝牙设备（如果已连接）
        audioRouteManager?.setAudioRouteToBluetooth()
        android.util.Log.d("VideoPresentation", "音频路由已配置")

        // 处理特殊平台的URL签名
        val finalUri = when {
            // 检测是否为Bilibili URL
            com.example.floatingscreencasting.dlna.BilibiliWbiSigner.isBilibiliUrl(uri) -> {
                android.util.Log.d("VideoPresentation", "========== 检测到Bilibili URL，开始WBI签名处理 ==========")
                try {
                    // BilibiliWbiSigner.fixBilibiliUrl是suspend函数，需要使用runBlocking调用
                    val fixedUrl = kotlinx.coroutines.runBlocking {
                        com.example.floatingscreencasting.dlna.BilibiliWbiSigner.fixBilibiliUrl(uri)
                    }
                    android.util.Log.d("VideoPresentation", "Bilibili URL已处理")
                    android.util.Log.d("VideoPresentation", "原始URL: $uri")
                    android.util.Log.d("VideoPresentation", "处理后URL: $fixedUrl")
                    fixedUrl
                } catch (e: Exception) {
                    android.util.Log.e("VideoPresentation", "Bilibili WBI签名处理失败", e)
                    uri
                }
            }
            // 检测是否为爱奇艺 URL
            uri.contains("iqiyi.com") || uri.contains("qiyi.com") -> {
                android.util.Log.d("VideoPresentation", "检测到爱奇艺URL，尝试修复签名")
                // 尝试修复签名
                com.example.floatingscreencasting.dlna.IqiyiSigner.fixIqiyiUrl(uri)
            }
            // 其他URL直接使用
            else -> uri
        }

        android.util.Log.d("VideoPresentation", "最终使用的URL: ${if (finalUri != uri) "已修复签名" else "原始URL"}")

        // 保存播放记录
        val title = extractTitleFromUri(finalUri)
        historyManager.savePlayback(finalUri, title, 0, 0)

        // 获取对应平台的请求头
        val headers = getHeadersForUrl(finalUri)
        android.util.Log.d("VideoPresentation", "使用请求头: ${headers.keys.joinToString()}")

        // 使用视频缓存管理器创建缓存数据源
        val cacheManager = VideoCacheManager.getInstance(context)
        val cachedDataSourceFactory = cacheManager.createCachedDataSourceFactory(context, headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(cachedDataSourceFactory)

        // 创建新的ExoPlayer实例
        releasePlayer()

        android.util.Log.d("VideoPresentation", "创建新的ExoPlayer实例")

        // 静音播放设置：由于车机系统限制，投屏视频无法输出到蓝牙耳机
        // 因此设置为静音播放，避免干扰其他应用的声音输出
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        android.util.Log.d("VideoPresentation", "ExoPlayer AudioAttributes: USAGE_MEDIA, CONTENT_TYPE_MOVIE, handleAudioFocus=true")
        android.util.Log.d("VideoPresentation", "注意：投屏视频将静音播放（车机系统限制）")
        android.util.Log.d("VideoPresentation", "使用outerContext创建ExoPlayer: $outerContext")

        // 重要：使用outerContext（MainActivity的Context）而不是Presentation的context
        // 这样ExoPlayer可以正确继承Activity的音频配置和路由
        val playerBuilder = ExoPlayer.Builder(outerContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)  // true = handleAudioFocus

        exoPlayer = playerBuilder.build()
            .apply {
                // 根据静音状态设置音量
                volume = if (isMuted) 0f else 1f
                android.util.Log.d("VideoPresentation", "ExoPlayer音量: ${if (isMuted) 0 else 1}, 静音状态: $isMuted")

                // 显示PlayerView并绑定ExoPlayer
                binding.playerView.isVisible = true
                binding.playerView.player = this
                android.util.Log.d("VideoPresentation", "PlayerView已显示并绑定ExoPlayer")

                // 添加状态监听器
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateName = when (playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN($playbackState)"
                        }
                        android.util.Log.d("VideoPresentation", "播放状态变化: $stateName, isPlaying=$isPlaying, playWhenReady=$playWhenReady")
                        when (playbackState) {
                            Player.STATE_READY -> {
                                if (!playWhenReady) {
                                    playWhenReady = true
                                }
                                if (!isPlaying) {
                                    play()
                                }
                            }
                            Player.STATE_ENDED -> {
                                android.util.Log.d("VideoPresentation", "播放结束")
                                // 播放结束，恢复用户设置的透明度
                                setWindowAlphaForPlaying(false)
                                // 显示等待提示
                                binding.waitingContainer.isVisible = true
                            }
                            Player.STATE_BUFFERING -> {
                                android.util.Log.d("VideoPresentation", "缓冲中...")
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        android.util.Log.d("VideoPresentation", "isPlaying变化: $isPlaying")
                        super.onIsPlayingChanged(isPlaying)
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("VideoPresentation", "播放器错误: ${error.message}", error)
                        android.util.Log.e("VideoPresentation", "错误代码: ${error.errorCode}")
                        android.util.Log.e("VideoPresentation", "错误原因: ${error.errorCodeName}")

                        // 播放失败，恢复用户设置的透明度
                        setWindowAlphaForPlaying(false)
                        // 显示等待提示
                        binding.waitingContainer.isVisible = true

                        // 通知MainActivity播放失败
                        try {
                            val intent = android.content.Intent("com.example.floatingscreencasting.PLAYBACK_ERROR")
                            intent.putExtra("error", error.message ?: "Unknown error")
                            intent.putExtra("errorCode", error.errorCode)
                            context.sendBroadcast(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("VideoPresentation", "发送错误广播失败", e)
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        android.util.Log.d("VideoPresentation", "视频尺寸: ${videoSize.width}x${videoSize.height}")
                    }

                    override fun onRenderedFirstFrame() {
                        android.util.Log.d("VideoPresentation", "首帧已渲染")
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: androidx.media3.common.Player.PositionInfo,
                        newPosition: androidx.media3.common.Player.PositionInfo,
                        reason: Int
                    ) {
                        // 播放位置变化时更新历史记录
                        if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                            updatePlaybackHistory()
                        }
                    }
                })

                // 定期保存播放进度（每10秒）
                Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                    override fun run() {
                        if (isPlaying()) {
                            updatePlaybackHistory()
                        }
                        Handler(Looper.getMainLooper()).postDelayed(this, 10000)
                    }
                }, 10000)
            }

        // 准备并播放
        exoPlayer?.apply {
            val mediaItem = MediaItem.fromUri(finalUri)
            setMediaItem(mediaItem)
            prepare()
            play()
            android.util.Log.d("VideoPresentation", "ExoPlayer已开始播放")
            android.util.Log.d("VideoPresentation", "是否正在播放: $isPlaying")
        } ?: run {
            android.util.Log.e("VideoPresentation", "ExoPlayer为null！")
        }

        // 隐藏等待提示
        binding.waitingContainer.isVisible = false

        // 播放时设置为完全不透明
        setWindowAlphaForPlaying(true)

        android.util.Log.d("VideoPresentation", "========== playMedia完成 ==========")
    }

    /**
     * 播放
     */
    fun play() {
        android.util.Log.d("VideoPresentation", "play() 调用 - isPlaying: ${isPlaying()}")
        exoPlayer?.play()
        android.util.Log.d("VideoPresentation", "play() 完成 - isPlaying: ${isPlaying()}")
    }

    /**
     * 暂停
     */
    fun pause() {
        android.util.Log.d("VideoPresentation", "pause() 调用 - isPlaying: ${isPlaying()}")
        exoPlayer?.pause()
        android.util.Log.d("VideoPresentation", "pause() 完成 - isPlaying: ${isPlaying()}")
    }

    /**
     * 设置静音状态
     */
    fun setMuted(muted: Boolean) {
        isMuted = muted
        exoPlayer?.volume = if (muted) 0f else 1f
        android.util.Log.d("VideoPresentation", "静音状态: $muted, 音量: ${if (muted) 0 else 1}")
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    /**
     * 停止
     */
    fun stop() {
        exoPlayer?.stop()
        // 停止播放时恢复用户设置的透明度
        setWindowAlphaForPlaying(false)
        // 重置音频路由
        audioRouteManager?.resetAudioRoute()
        // 恢复A2DP静音状态（可选，根据需要决定是否恢复）
        // audioRouteManager?.setA2DPMuted(true)
    }

    /**
     * 重置到初始状态（等待投屏状态）
     * 停止播放，显示加载动画，恢复用户设置的透明度
     */
    fun resetToInitialState() {
        android.util.Log.d("VideoPresentation", "========== resetToInitialState开始 ==========")

        // 停止播放
        exoPlayer?.stop()

        // 清除当前URI，确保下次投屏可以重新加载
        currentUri = ""
        currentReferer = ""

        // 显示等待投屏的加载动画
        binding.waitingContainer?.isVisible = true

        // 隐藏PlayerView
        binding.playerView?.isVisible = false

        // 恢复用户设置的透明度
        updateWindowAlpha()

        // 重置音频路由
        audioRouteManager?.resetAudioRoute()

        android.util.Log.d("VideoPresentation", "========== resetToInitialState完成 ==========")
    }

    /**
     * 设置窗口透明度（播放状态）
     * @param isPlaying true=播放中（不透明），false=未播放（使用用户设置的透明度）
     */
    private fun setWindowAlphaForPlaying(isPlaying: Boolean) {
        if (window != null && isShowing) {
            val alpha = if (isPlaying) 1.0f else userAlpha
            window?.attributes = window?.attributes?.apply {
                this.alpha = alpha
            }
            android.util.Log.d("VideoPresentation", "窗口透明度设置: $alpha (播放中=$isPlaying)")
        }
    }

    /**
     * 上一个
     */
    fun previous() {
        exoPlayer?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                // 如果没有上一个， seek到开头
                player.seekTo(0)
            }
        }
    }

    /**
     * 下一个
     */
    fun next() {
        exoPlayer?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
        }
    }

    /**
     * 获取ExoPlayer实例
     */
    fun getExoPlayer(): ExoPlayer? {
        return exoPlayer
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    /**
     * 处理来自手机端的远程控制命令
     */
    fun handleRemoteCommand(action: String, params: Map<String, Any>) {
        android.util.Log.d("VideoPresentation", "远程命令: $action $params")
        when (action) {
            "play" -> play()
            "pause" -> pause()
            "stop" -> {
                stop()
                resetToInitialState()
            }
            "seek" -> {
                val positionMs = (params["position_ms"] as? Number)?.toLong() ?: 0
                seekTo(positionMs)
            }
        }
    }

    /**
     * 释放播放器资源
     */
    private fun releasePlayer() {
        // 停止同步服务器
        syncServer?.stop()
        syncServer = null

        exoPlayer?.release()
        exoPlayer = null
    }

    /**
     * 处理媒体按键事件（方向盘多功能按键）
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        android.util.Log.d("VideoPresentation", "onKeyDown: keyCode=$keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                // 播放/暂停切换
                if (isPlaying()) {
                    pause()
                    android.util.Log.d("VideoPresentation", "媒体按键：暂停")
                } else {
                    play()
                    android.util.Log.d("VideoPresentation", "媒体按键：播放")
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                play()
                android.util.Log.d("VideoPresentation", "媒体按键：播放")
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                pause()
                android.util.Log.d("VideoPresentation", "媒体按键：暂停")
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                next()
                android.util.Log.d("VideoPresentation", "媒体按键：下一个")
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                previous()
                android.util.Log.d("VideoPresentation", "媒体按键：上一个")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /**
     * 更新播放历史记录
     */
    private fun updatePlaybackHistory() {
        if (currentUri.isEmpty()) return

        val player = exoPlayer ?: return
        val currentPosition = player.currentPosition
        val duration = player.duration

        if (duration > 0) {
            val title = extractTitleFromUri(currentUri)
            historyManager.updateProgress(currentUri, currentPosition, duration)
            android.util.Log.d("VideoPresentation", "更新播放历史: $title, 进度: ${currentPosition / 1000}s / ${duration / 1000}s")
        }

        // 同步播放进度到手机端
        syncProgress()
    }

    /**
     * 同步播放进度到手机端
     */
    private fun syncProgress() {
        val player = exoPlayer ?: return

        syncServer?.broadcast(
            com.example.floatingscreencasting.dlna.SyncServer.SyncMessage.Progress(
                position = player.currentPosition,
                duration = player.duration,
                isPlaying = player.isPlaying
            )
        )
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
            uri.contains("mgtv") -> "芒果TV"
            else -> "在线视频"
        }
    }

    /**
     * 获取最后播放的记录
     */
    fun getLastPlayed(): PlaybackHistoryManager.PlaybackRecord? {
        return historyManager.getLastPlayed()
    }

    /**
     * 检查是否有可以继续观看的内容
     */
    fun hasContinueWatching(): Boolean {
        return historyManager.hasContinueWatching()
    }

}
