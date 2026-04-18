package com.example.floatingscreencasting.domain.model

/**
 * 播放状态领域模型
 * 表示当前的播放状态
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val uri: String = "",
    val title: String = ""
) {
    /**
     * 获取播放进度（百分比）
     */
    fun getProgressPercent(): Int {
        return if (duration > 0) {
            ((currentPosition.toFloat() / duration) * 100).toInt()
        } else {
            0
        }
    }

    /**
     * 获取当前播放位置（秒）
     */
    fun getCurrentPositionInSeconds(): Long = currentPosition / 1000

    /**
     * 获取总时长（秒）
     */
    fun getDurationInSeconds(): Long = duration / 1000

    /**
     * 检查是否正在播放
     */
    fun isActive(): Boolean = isPlaying && currentPosition >= 0

    /**
     * 检查是否已静音
     */
    fun isVolumeMuted(): Boolean = isMuted || volume == 0f

    /**
     * 获取格式化的播放时间字符串
     */
    fun getFormattedPosition(): String {
        return formatTime(currentPosition)
    }

    /**
     * 获取格式化的总时长字符串
     */
    fun getFormattedDuration(): String {
        return formatTime(duration)
    }

    /**
     * 格式化时间为 HH:MM:SS 或 MM:SS
     */
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    companion object {
        /**
         * 创建一个初始播放状态
         */
        fun initial() = PlaybackState()

        /**
         * 创建一个播放中的状态
         */
        fun playing(uri: String, title: String) = PlaybackState(
            isPlaying = true,
            uri = uri,
            title = title
        )

        /**
         * 创建一个暂停的状态
         */
        fun paused(uri: String, title: String, position: Long) = PlaybackState(
            isPlaying = false,
            uri = uri,
            title = title,
            currentPosition = position
        )
    }
}
