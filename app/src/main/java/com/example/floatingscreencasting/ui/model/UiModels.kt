package com.example.floatingscreencasting.ui.model

/**
 * 主界面UI状态
 * 包含所有界面状态的不可变数据类
 */
data class MainUiState(
    val isWindowVisible: Boolean = false,
    val castingStatus: String = "等待投屏",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isMuted: Boolean = true,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val windowX: Int = 428,
    val windowY: Int = 332,
    val windowWidth: Int = 434,
    val windowHeight: Int = 244,
    val windowAlpha: Float = 0.41f,
    val currentAudioOutput: String = "系统扬声器",
    val selectedDisplayId: Int = 2,
    val availableDisplays: List<DisplayInfo> = emptyList(),
    val hasContinueWatching: Boolean = false,
    val lastPlayedTitle: String = "",
    val lastPlayedProgress: Int = 0,
    val audioOutputMode: String = "bilibili",  // 默认原源模式
    val connectedPhoneDevice: String? = null,
    val webSocketClientCount: Int = 0,  // WebSocket连接的手机数量

    // 当前播放视频信息
    val currentVideoTitle: String = "",
    val currentVideoUrl: String = "",

    // 悬浮窗启用状态
    val isFloatingWindowEnabled: Boolean = true,

    // WebSocket服务器运行状态
    val isWebSocketServerRunning: Boolean = false  // 默认false，启动成功后更新为true
)

/**
 * 屏幕比例枚举
 */
enum class AspectRatio(val displayName: String, val width: Int, val height: Int) {
    RATIO_16_9("16:9", 16, 9),
    RATIO_4_3("4:3", 4, 3),
    RATIO_PORTRAIT("竖屏", 9, 16),
    CUSTOM("自定义", 0, 0)
}

/**
 * 显示器信息
 */
data class DisplayInfo(
    val id: Int,
    val name: String
) {
    override fun toString(): String = "$name (ID: $id)"
}
