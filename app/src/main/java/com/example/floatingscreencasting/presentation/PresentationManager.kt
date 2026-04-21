package com.example.floatingscreencasting.presentation

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import com.example.floatingscreencasting.service.CastingForegroundService

/**
 * Presentation管理器
 *
 * 功能：
 * 1. 管理Presentation的生命周期
 * 2. 处理多屏幕显示
 * 3. 与ForegroundService协同工作，确保后台也能显示
 */
class PresentationManager(private val context: Context) {

    companion object {
        private const val TAG = "PresentationManager"
        private const val DEFAULT_DISPLAY_ID = 2 // 驾驶屏ID
    }

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var presentation: VideoPresentation? = null

    /**
     * 显示Presentation
     * @param displayId 目标Display ID，默认为2（驾驶屏）
     * @return 是否成功显示
     */
    fun showPresentation(displayId: Int = DEFAULT_DISPLAY_ID): Boolean {
        Log.d(TAG, "========== showPresentation开始 ==========")
        Log.d(TAG, "目标Display ID: $displayId")

        // 如果已有Presentation显示，先隐藏
        if (presentation != null && presentation!!.isShowing) {
            Log.d(TAG, "已有Presentation显示，先隐藏")
            hidePresentation()
        }

        // 查找目标显示
        val displays = displayManager.displays
        Log.d(TAG, "系统可用Display数量: ${displays.size}")

        val targetDisplay = displays.find { it.displayId == displayId }

        if (targetDisplay == null) {
            Log.e(TAG, "未找到Display ID: $displayId")
            Log.e(TAG, "可用Display IDs: ${displays.map { it.displayId }}")

            // 尝试使用Presentation类型的Display作为fallback
            val presentationDisplay = displays.find {
                it.flags and android.view.Display.FLAG_PRESENTATION != 0
            }

            if (presentationDisplay != null) {
                Log.w(TAG, "使用Presentation Display作为fallback: ${presentationDisplay.displayId}")
                createAndShowPresentation(presentationDisplay)
                return true
            }

            return false
        }

        Log.d(TAG, "找到目标Display: ${targetDisplay.name}")
        createAndShowPresentation(targetDisplay)

        return true
    }

    /**
     * 创建并显示Presentation
     */
    private fun createAndShowPresentation(display: android.view.Display) {
        Log.d(TAG, "创建Presentation，Display ID: ${display.displayId}")

        presentation = VideoPresentation(context, display)

        try {
            presentation?.show()
            Log.i(TAG, "Presentation显示成功")

            // 同步到ForegroundService
            CastingForegroundService.updateStatus(context, false)

        } catch (e: Exception) {
            Log.e(TAG, "Presentation显示失败", e)
            presentation = null
        }
    }

    /**
     * 隐藏Presentation
     */
    fun hidePresentation() {
        Log.d(TAG, "========== hidePresentation开始 ==========")

        presentation?.let {
            Log.d(TAG, "Presentation当前状态: isShowing=${it.isShowing}")

            if (it.isShowing) {
                it.dismiss()
                Log.i(TAG, "Presentation已隐藏")
            }

            presentation = null
        }
    }

    /**
     * 获取Presentation实例
     */
    fun getPresentation(): VideoPresentation? = presentation

    /**
     * 检查Presentation是否正在显示
     */
    fun isPresentationShowing(): Boolean = presentation?.isShowing == true

    /**
     * 更新窗口位置
     */
    fun updateWindowPosition(x: Int, y: Int) {
        presentation?.let {
            it.windowX = x
            it.windowY = y
        }
    }

    /**
     * 更新窗口大小
     */
    fun updateWindowSize(width: Int, height: Int) {
        presentation?.let {
            it.windowWidth = width
            it.windowHeight = height
        }
    }

    /**
     * 更新窗口透明度
     */
    fun updateWindowAlpha(alpha: Float) {
        presentation?.let {
            it.windowAlpha = alpha
        }
    }

    /**
     * 设置静音状态
     */
    fun setMuted(muted: Boolean) {
        presentation?.setMuted(muted)
    }

    /**
     * 播放媒体
     */
    fun playMedia(uri: String, title: String = "", durationMs: Long = 0, initialPositionMs: Long = 0) {
        Log.d(TAG, "playMedia: uri=$uri, title=$title")
        presentation?.playMedia(uri, title, durationMs, initialPositionMs)
    }

    /**
     * 播放
     */
    fun play() {
        presentation?.play()
    }

    /**
     * 暂停
     */
    fun pause() {
        presentation?.pause()
    }

    /**
     * 停止
     */
    fun stop() {
        presentation?.stop()
    }

    /**
     * 跳转
     */
    fun seekTo(positionMs: Long) {
        presentation?.seekTo(positionMs)
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = presentation?.isPlaying() == false

    /**
     * 获取当前播放位置
     */
    fun getCurrentPosition(): Long {
        return presentation?.getExoPlayer()?.currentPosition ?: 0L
    }

    /**
     * 获取当前视频标题
     */
    fun getCurrentTitle(): String = presentation?.getCurrentTitle() ?: ""

    /**
     * 获取当前视频时长
     */
    fun getCurrentDurationMs(): Long = presentation?.getCurrentDurationMs() ?: 0L

    /**
     * 清理资源
     */
    fun release() {
        Log.d(TAG, "========== release开始 ==========")
        hidePresentation()
        Log.d(TAG, "PresentationManager已释放")
    }
}
