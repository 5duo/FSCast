/**
 * 数据模型定义
 */

// 车机发现信息
export class DiscoverInfo {
  name: string = ''
  ip: string = ''
  port: number = 0
  version: number = 1
}

// 播放状态（从车机端状态同步帧解析）
export class PlaybackState {
  isPlaying: boolean = false
  positionMs: number = 0
  durationMs: number = 0
  title: string = ''
  bufferedPositionMs: number = 0
  audioOutput: string = 'speaker'       // 'speaker' 或 'phone'
  selectedDevice: string = ''
}

// 连接信息
export class ConnectionInfo {
  isConnected: boolean = false
  serverIp: string = ''
  serverPort: number = 19880
  serverName: string = ''
}

// 音频格式
export class AudioFormat {
  sampleRate: number = 44100
  channels: number = 2
  encoding: number = 2   // PCM_16BIT

  equals(other: AudioFormat): boolean {
    return this.sampleRate === other.sampleRate &&
      this.channels === other.channels &&
      this.encoding === other.encoding
  }
}

// 命令执行结果
export class CommandResult {
  action: string = ''
  success: boolean = false
  positionMs: number = 0
  output: string = ''
  error: string = ''
}

// 连接状态枚举
export enum ConnectionState {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  RECONNECTING
}
