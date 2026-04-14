package com.example.floatingscreencasting.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * 偏好设置管理器
 * 用于持久化存储悬浮窗设置
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "floating_window_prefs"
        private const val KEY_WINDOW_X = "window_x"
        private const val KEY_WINDOW_Y = "window_y"
        private const val KEY_WINDOW_WIDTH = "window_width"
        private const val KEY_WINDOW_HEIGHT = "window_height"
        private const val KEY_WINDOW_ALPHA = "window_alpha"
        private const val KEY_ASPECT_RATIO = "aspect_ratio"

        // 默认值
        private const val DEFAULT_X = 720
        private const val DEFAULT_Y = 220
        private const val DEFAULT_WIDTH = 480
        private const val DEFAULT_HEIGHT = 270
        private const val DEFAULT_ALPHA = 100
        private const val DEFAULT_ASPECT_RATIO = "16:9"
    }

    /**
     * 悬浮窗位置和大小
     */
    var windowPosition: WindowPosition
        get() {
            val x = prefs.getInt(KEY_WINDOW_X, DEFAULT_X)
            val y = prefs.getInt(KEY_WINDOW_Y, DEFAULT_Y)
            val width = prefs.getInt(KEY_WINDOW_WIDTH, DEFAULT_WIDTH)
            val height = prefs.getInt(KEY_WINDOW_HEIGHT, DEFAULT_HEIGHT)
            val alpha = prefs.getInt(KEY_WINDOW_ALPHA, DEFAULT_ALPHA)
            val aspectRatio = prefs.getString(KEY_ASPECT_RATIO, DEFAULT_ASPECT_RATIO) ?: DEFAULT_ASPECT_RATIO
            return WindowPosition(x, y, width, height, alpha, aspectRatio)
        }
        set(value) {
            prefs.edit().apply {
                putInt(KEY_WINDOW_X, value.x)
                putInt(KEY_WINDOW_Y, value.y)
                putInt(KEY_WINDOW_WIDTH, value.width)
                putInt(KEY_WINDOW_HEIGHT, value.height)
                putInt(KEY_WINDOW_ALPHA, value.alpha)
                putString(KEY_ASPECT_RATIO, value.aspectRatio)
                apply()
            }
        }

    /**
     * 窗口位置数据类
     */
    data class WindowPosition(
        var x: Int = DEFAULT_X,
        var y: Int = DEFAULT_Y,
        var width: Int = DEFAULT_WIDTH,
        var height: Int = DEFAULT_HEIGHT,
        var alpha: Int = DEFAULT_ALPHA,
        var aspectRatio: String = DEFAULT_ASPECT_RATIO
    )

    /**
     * 根据宽高比计算对应的高度
     */
    fun calculateHeightForWidth(width: Int, aspectRatio: String): Int {
        return when (aspectRatio) {
            "16:9" -> (width * 9) / 16
            "4:3" -> (width * 3) / 4
            "9:16" -> (width * 16) / 9  // 竖屏
            else -> (width * 9) / 16
        }
    }

    /**
     * 根据高度计算对应的宽度
     */
    fun calculateWidthForHeight(height: Int, aspectRatio: String): Int {
        return when (aspectRatio) {
            "16:9" -> (height * 16) / 9
            "4:3" -> (height * 4) / 3
            "9:16" -> (height * 9) / 16  // 竖屏
            else -> (height * 16) / 9
        }
    }
}
