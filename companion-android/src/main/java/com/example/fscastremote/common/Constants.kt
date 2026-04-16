package com.example.fscastremote.common

/**
 * 音频流协议常量
 * 与车机端、鸿蒙手机端共享
 */
object Constants {
    // 端口号
    const val UDP_PORT = 19876
    const val TCP_PORT = 19880

    // 协议版本
    const val PROTOCOL_VERSION = 1

    // 平台标识
    const val PLATFORM = "android"

    // UDP 发现消息
    val DISCOVER_MESSAGE = "FSCAST_DISCOVER".toByteArray()

    // 帧类型
    const val FRAME_TYPE_HEARTBEAT: Byte = 0x00
    const val FRAME_TYPE_FORMAT_HEADER: Byte = 0x01
    const val FRAME_TYPE_PCM_DATA: Byte = 0x02
    const val FRAME_TYPE_FORMAT_CHANGE: Byte = 0x03
    const val FRAME_TYPE_STATE_SYNC: Byte = 0x10
    const val FRAME_TYPE_COMMAND_RESULT: Byte = 0x11
    const val FRAME_TYPE_OUTPUT_CHANGED: Byte = 0x12
    const val FRAME_TYPE_COMMAND: Byte = 0x20

    // 心跳间隔（毫秒）
    const val HEARTBEAT_INTERVAL_MS = 3000L

    // 连接超时（毫秒）
    const val CONNECT_TIMEOUT_MS = 6000L

    // UDP 发现超时（毫秒）
    const val DISCOVER_TIMEOUT_MS = 3000L

    // PCM 音频参数
    const val PCM_BUFFER_SIZE = 176400 // ~500ms @ 44.1kHz stereo 16bit
    const val AUDIO_TRACK_BUFFER_SIZE = 2 * 44100 * 2 * 2 // ~2秒缓冲

    // 音频编码格式
    const val ENCODING_PCM_16BIT = 2
}
