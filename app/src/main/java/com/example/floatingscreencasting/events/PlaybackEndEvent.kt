package com.example.floatingscreencasting.events

/**
 * 播放结束事件
 * 用于通知MainActivity视频播放已结束
 */
data class PlaybackEndEvent(
    val position: Long = 0L  // 播放结束时的位置（通常是视频总时长）
)
