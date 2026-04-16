/**
 * FSCast Remote 通信协议常量
 * 与车机端 docs/protocol/AUDIO_STREAMING_PROTOCOL.md 保持一致
 */

// 端口
export const UDP_PORT: number = 19876
export const TCP_PORT: number = 19880

// 协议版本
export const PROTOCOL_VERSION: number = 1

// 帧类型
export const FRAME_TYPE_HEARTBEAT: number = 0x00
export const FRAME_TYPE_FORMAT_HEADER: number = 0x01
export const FRAME_TYPE_PCM_DATA: number = 0x02
export const FRAME_TYPE_FORMAT_CHANGE: number = 0x03
export const FRAME_TYPE_STATE_SYNC: number = 0x10
export const FRAME_TYPE_COMMAND_RESULT: number = 0x11
export const FRAME_TYPE_OUTPUT_CHANGED: number = 0x12
export const FRAME_TYPE_COMMAND: number = 0x20
export const FRAME_TYPE_BUFFER_STATUS: number = 0x21  // 缓冲区状态报告（手机→车机）

// 心跳
export const HEARTBEAT_INTERVAL_MS: number = 3000
export const BUFFER_STATUS_INTERVAL_MS: number = 500  // 缓冲区状态报告间隔（500ms）
export const HEARTBEAT_TIMEOUT_MS: number = 5000

// 发现
export const DISCOVER_MESSAGE: string = 'FSCAST_DISCOVER'

// 应用信息
export const APP_NAME: string = 'FSCast Remote'
export const DEVICE_NAME: string = 'FSCast Remote'
export const PLATFORM: string = 'harmony'

// 音频格式
export const AUDIO_SAMPLE_FORMAT_S16LE: number = 2  // PCM 16-bit little-endian
