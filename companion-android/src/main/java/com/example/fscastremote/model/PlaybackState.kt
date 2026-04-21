package com.example.fscastremote.model

/**
 * 播放状态
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val title: String = "",
    val bufferedPositionMs: Long = 0,
    val audioOutput: String = "speaker", // "phone" 或 "speaker"
    val selectedDevice: String = ""
)

/**
 * 命令执行结果
 */
data class CommandResult(
    val action: String = "",
    val success: Boolean = false,
    val positionMs: Long = 0,
    val output: String = "",
    val error: String = ""
)

/**
 * 连接状态
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/**
 * PCM 音频格式
 * 避免与 Android 的 AudioFormat 类冲突
 */
data class PcmAudioFormat(
    var sampleRate: Int = 44100,
    var channels: Int = 2,
    var encoding: Int = 2 // ENCODING_PCM_16BIT
)
