package com.example.floatingscreencasting.presentation

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
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
import com.example.floatingscreencasting.R
import com.example.floatingscreencasting.cache.VideoCacheManager
import com.example.floatingscreencasting.databinding.PresentationVideoBinding
import com.example.floatingscreencasting.history.PlaybackHistoryManager

/**
 * 单屏幕视频播放Dialog
 * 用于在主屏幕上显示视频播放窗口
 */
class SingleScreenVideoDialog(
    private val outerContext: Context
) : Dialog(outerContext, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private var _binding: PresentationVideoBinding? = null
    private val binding get() = _binding!!

    private var exoPlayer: ExoPlayer? = null

    // 播放历史管理器
    private val historyManager: PlaybackHistoryManager = PlaybackHistoryManager.getInstance(outerContext)

    // 当前播放的URL
    private var currentUri: String = ""

    // 静音状态
    private var isMuted: Boolean = true

    // 窗口位置和大小
    var windowX: Int = 0
    var windowY: Int = 0
    var windowWidth: Int = 0
    var windowHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = PresentationVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置窗口全屏
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 初始化播放器
        initializePlayer()

        // 显示等待界面
        binding.waitingContainer.isVisible = true
    }

    /**
     * 初始化ExoPlayer
     */
    private fun initializePlayer() {
        val context = context.applicationContext

        // 创建HTTP数据源工厂
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30 * 1000)
            .setReadTimeoutMs(30 * 1000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 12) FSCast/1.0")

        // 使用DefaultDataSource包装，支持HTTP和其他协议
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            httpDataSourceFactory
        )

        // 创建支持HLS的MediaSourceFactory
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // 创建ExoPlayer
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // 绑定到PlayerView
        binding.playerView.player = exoPlayer
        binding.playerView.useController = true
        binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

        // 设置播放器监听器
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.waitingContainer.isVisible = !isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // 播放结束
                    binding.waitingContainer.isVisible = true
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // 更新播放历史
                exoPlayer?.currentPosition?.let { pos ->
                    exoPlayer?.duration?.let { dur ->
                        if (dur > 0) {
                            val currentUri = exoPlayer?.currentMediaItem?.localConfiguration?.uri.toString()
                            historyManager.savePlayback(
                                uri = currentUri,
                                title = mediaMetadata.title?.toString() ?: "未知视频",
                                positionMs = pos,
                                durationMs = dur
                            )
                        }
                    }
                }
            }
        })
    }

    /**
     * 播放媒体
     */
    fun playMedia(uri: String) {
        currentUri = uri

        // 保存播放记录
        val title = extractTitleFromUri(uri)
        historyManager.savePlayback(uri, title, 0, 0)

        // 使用视频缓存管理器创建缓存数据源
        val cacheManager = VideoCacheManager.getInstance(context)
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 12) FSCast/1.0",
            "Referer" to extractRefererFromUri(uri)
        )
        val cachedDataSourceFactory = cacheManager.createCachedDataSourceFactory(context, headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(cachedDataSourceFactory)

        // 创建新的ExoPlayer实例
        releasePlayer()

        // 创建新的ExoPlayer
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // 绑定到PlayerView
        binding.playerView.player = exoPlayer
        binding.playerView.useController = true

        // 创建MediaItem并播放
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()

        if (isMuted) {
            exoPlayer?.volume = 0f
        } else {
            exoPlayer?.volume = 1f
        }

        exoPlayer?.play()
    }

    /**
     * 从URI提取标题
     */
    private fun extractTitleFromUri(uri: String): String {
        return try {
            val segments = uri.split("/")
            segments.last().substring(0, 50)
        } catch (e: Exception) {
            "未知视频"
        }
    }

    /**
     * 从URI提取Referer
     */
    private fun extractRefererFromUri(uri: String): String {
        return when {
            uri.contains("bilibili.com") || uri.contains("bilibili") -> "https://www.bilibili.com"
            uri.contains("acgvideo.com") -> "https://www.acgvideo.com"
            else -> ""
        }
    }

    /**
     * 设置静音
     */
    fun setMuted(muted: Boolean) {
        isMuted = muted
        exoPlayer?.volume = if (muted) 0f else 1f
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    /**
     * 暂停播放
     */
    fun pause() {
        exoPlayer?.pause()
    }

    /**
     * 恢复播放
     */
    fun play() {
        exoPlayer?.play()
    }

    /**
     * 停止播放
     */
    fun stop() {
        exoPlayer?.stop()
        binding.waitingContainer.isVisible = true
    }

    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    /**
     * 获取ExoPlayer实例
     */
    fun getExoPlayer(): ExoPlayer? = exoPlayer

    /**
     * 释放播放器
     */
    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun dismiss() {
        releasePlayer()
        _binding = null
        super.dismiss()
    }
}
