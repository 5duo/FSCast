/**
 * TCP 音频流客户端
 * 连接车机，接收 PCM 音频 + 状态同步，发送控制命令
 */
import { socket } from '@kit.NetworkKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { util } from '@kit.ArkTS'
import {
  TCP_PORT, PROTOCOL_VERSION, PLATFORM,
  FRAME_TYPE_HEARTBEAT, FRAME_TYPE_FORMAT_HEADER, FRAME_TYPE_PCM_DATA,
  FRAME_TYPE_FORMAT_CHANGE, FRAME_TYPE_STATE_SYNC, FRAME_TYPE_COMMAND_RESULT,
  FRAME_TYPE_OUTPUT_CHANGED, FRAME_TYPE_COMMAND, FRAME_TYPE_BUFFER_STATUS,
  HEARTBEAT_INTERVAL_MS, BUFFER_STATUS_INTERVAL_MS
} from '../common/Constants'
import {
  PlaybackState, AudioFormat, CommandResult, ConnectionState
} from '../model/Models'

const TAG = 'AudioStreamClient'

// Socket 消息信息类型
interface SocketMessageInfo {
  type: string
  length: number
  message: ArrayBuffer
  remoteInfo: socket.SocketRemoteInfo
}

// TCP 消息回调值类型
interface TCPMessageValue {
  message: ArrayBuffer
  remoteInfo: socket.SocketRemoteInfo
}

// 命令载荷类型
interface CommandPayload {
  type: string
  action?: string
  device_name?: string
  platform?: string
  version?: string
  output?: string
  position_ms?: number | string
}

export class AudioStreamClient {
  private tcpSocket: socket.TCPSocket | null = null
  private isConnected: boolean = false
  private serverIp: string = ''
  private serverPort: number = TCP_PORT

  // 接收缓冲区（处理 TCP 粘包/拆包）
  private recvBuffer: ArrayBuffer = new ArrayBuffer(0)

  // 心跳定时器
  private heartbeatTimer: number = -1

  // 缓冲区状态报告定时器
  private bufferStatusTimer: number = -1

  // 当前缓冲区大小（由外部设置，用于报告给车机）
  private currentBufferSize: number = 0
  private maxBufferSize: number = 176400 * 6  // 44.1kHz * 2ch * 2byte * 6秒，与 AudioPlayer.MAX_QUEUE_SIZE 一致

  // 统计信息
  private pcmFramesReceived: number = 0
  private totalPcmBytes: number = 0
  private lastStatsTime: number = 0

  // 回调
  onFormatHeader: ((format: AudioFormat) => void) | null = null
  onPcmData: ((data: ArrayBuffer, timestamp: number) => void) | null = null  // 添加时间戳参数
  onFormatChange: ((format: AudioFormat) => void) | null = null
  onStateUpdate: ((state: PlaybackState) => void) | null = null
  onCommandResult: ((result: CommandResult) => void) | null = null
  onOutputChanged: ((output: string) => void) | null = null
  onConnectionChanged: ((state: ConnectionState) => void) | null = null

  /**
   * 连接车机
   */
  async connect(ip: string, port: number = TCP_PORT): Promise<void> {
    this.serverIp = ip
    this.serverPort = port
    this.onConnectionChanged?.(ConnectionState.CONNECTING)

    try {
      this.tcpSocket = socket.constructTCPSocketInstance()

      // 订阅事件
      this.tcpSocket.on('message', (value: TCPMessageValue) => {
        this.handleMessage(value.message)
      })

      this.tcpSocket.on('connect', () => {
        console.info(TAG, 'TCP connected')
        this.isConnected = true
        this.onConnectionChanged?.(ConnectionState.CONNECTED)
        this.resetStats()
        this.startHeartbeat()
        this.startBufferStatusUpdates()
      })

      this.tcpSocket.on('close', () => {
        console.info(TAG, 'TCP closed')
        this.logStats()
        this.handleDisconnect()
      })

      this.tcpSocket.on('error', (err: BusinessError) => {
        console.error(TAG, 'TCP error: ' + err.message)
        this.logStats()
        this.handleDisconnect()
      })

      // 连接
      let connectOptions: socket.TCPConnectOptions = {
        address: { address: ip, port: port } as socket.NetAddress,
        timeout: 6000
      }
      await this.tcpSocket.connect(connectOptions)
    } catch (error) {
      console.error(TAG, 'Connect failed: ' + (error as BusinessError).message)
      this.handleDisconnect()
      throw new Error('Connect failed: ' + (error as BusinessError).message)
    }
  }

  /**
   * 重置统计信息
   */
  private resetStats(): void {
    this.pcmFramesReceived = 0
    this.totalPcmBytes = 0
    this.lastStatsTime = Date.now()
  }

  /**
   * 记录统计信息
   */
  private logStats(): void {
    const elapsed = Date.now() - this.lastStatsTime
    if (elapsed > 0) {
      const rateKbps = (this.totalPcmBytes * 8 / elapsed / 1000).toFixed(2)
      console.info(TAG, `Stats: ${this.pcmFramesReceived} frames, ${this.totalPcmBytes} bytes, ${rateKbps} kbps`)
    }
  }

  /**
   * 处理接收到的数据（可能包含多个帧或部分帧）
   */
  private handleMessage(data: ArrayBuffer): void {
    // 追加到缓冲区
    let newBuffer = new ArrayBuffer(this.recvBuffer.byteLength + data.byteLength)
    let newView = new Uint8Array(newBuffer)
    newView.set(new Uint8Array(this.recvBuffer), 0)
    newView.set(new Uint8Array(data), this.recvBuffer.byteLength)
    this.recvBuffer = newBuffer

    // 尝试解析帧
    this.parseFrames()
  }

  /**
   * 从缓冲区解析完整帧
   */
  private parseFrames(): void {
    while (this.recvBuffer.byteLength > 0) {
      let view = new DataView(this.recvBuffer)
      let frameType = view.getUint8(0)
      let consumed = 0

      switch (frameType) {
        case FRAME_TYPE_HEARTBEAT: {
          // 心跳帧：1 字节
          consumed = 1
          break
        }
        case FRAME_TYPE_FORMAT_HEADER:
        case FRAME_TYPE_FORMAT_CHANGE: {
          // 格式帧：1 + 4 + 4 + 4 = 13 字节
          if (this.recvBuffer.byteLength < 13) return
          let format = new AudioFormat()
          format.sampleRate = view.getInt32(1)
          format.channels = view.getInt32(5)
          format.encoding = view.getInt32(9)
          consumed = 13
          const frameTypeName = frameType === FRAME_TYPE_FORMAT_HEADER ? "FORMAT_HEADER" : "FORMAT_CHANGE"
          console.info(TAG, `[${frameTypeName}] ${format.sampleRate}Hz, ${format.channels}ch, encoding=${format.encoding}`)
          if (frameType === FRAME_TYPE_FORMAT_HEADER) {
            this.onFormatHeader?.(format)
          } else {
            this.onFormatChange?.(format)
          }
          break
        }
        case FRAME_TYPE_PCM_DATA: {
          // PCM 数据帧：1 + 4 + 8 + N 字节（添加时间戳）
          if (this.recvBuffer.byteLength < 13) return
          let dataLength = view.getInt32(1)
          let timestamp = view.getBigInt64(5)  // 读取 8 字节时间戳
          if (this.recvBuffer.byteLength < 13 + dataLength) return
          let pcmData = this.recvBuffer.slice(13, 13 + dataLength)
          consumed = 13 + dataLength

          // 统计
          this.pcmFramesReceived++
          this.totalPcmBytes += dataLength

          // 每 5 秒记录一次统计（添加缓冲区信息）
          const now = Date.now()
          if (now - this.lastStatsTime > 5000) {
            const elapsed = (now - this.lastStatsTime) / 1000
            const rateKbps = (this.totalPcmBytes * 8 / elapsed / 1000).toFixed(2)
            console.info(TAG, `PCM: ${this.pcmFramesReceived} frames, ${dataLength} bytes, ${rateKbps} kbps, avg=${Math.floor(this.totalPcmBytes / elapsed)} B/s, ts=${timestamp}`)
            this.pcmFramesReceived = 0
            this.totalPcmBytes = 0
            this.lastStatsTime = now
          }

          this.onPcmData?.(pcmData, Number(timestamp))
          break
        }
        case FRAME_TYPE_STATE_SYNC: {
          // 状态同步帧：1 + 4 + N 字节 JSON
          if (this.recvBuffer.byteLength < 5) return
          let jsonLen = view.getInt32(1)
          if (this.recvBuffer.byteLength < 5 + jsonLen) return
          let jsonStr = this.arrayBufferToString(this.recvBuffer.slice(5, 5 + jsonLen))
          consumed = 5 + jsonLen
          this.parseStateSync(jsonStr)
          break
        }
        case FRAME_TYPE_COMMAND_RESULT: {
          // 命令结果帧：1 + 4 + N 字节 JSON
          if (this.recvBuffer.byteLength < 5) return
          let cmdJsonLen = view.getInt32(1)
          if (this.recvBuffer.byteLength < 5 + cmdJsonLen) return
          let cmdJsonStr = this.arrayBufferToString(this.recvBuffer.slice(5, 5 + cmdJsonLen))
          consumed = 5 + cmdJsonLen
          this.parseCommandResult(cmdJsonStr)
          break
        }
        case FRAME_TYPE_OUTPUT_CHANGED: {
          // 输出变化帧：1 + 4 + N 字节 JSON
          if (this.recvBuffer.byteLength < 5) return
          let outJsonLen = view.getInt32(1)
          if (this.recvBuffer.byteLength < 5 + outJsonLen) return
          let outJsonStr = this.arrayBufferToString(this.recvBuffer.slice(5, 5 + outJsonLen))
          consumed = 5 + outJsonLen
          this.parseOutputChanged(outJsonStr)
          break
        }
        default: {
          // 未知帧类型，跳过 1 字节
          console.warn(TAG, 'Unknown frame type: 0x' + frameType.toString(16))
          consumed = 1
          break
        }
      }

      if (consumed <= 0) break
      // 移除已消费的数据
      this.recvBuffer = this.recvBuffer.slice(consumed)
    }
  }

  /**
   * 解析状态同步 JSON
   */
  private parseStateSync(jsonStr: string): void {
    try {
      let obj = JSON.parse(jsonStr) as Record<string, Object>
      let state = new PlaybackState()
      state.isPlaying = (obj['is_playing'] as boolean) || false
      state.positionMs = (obj['position_ms'] as number) || 0
      state.durationMs = (obj['duration_ms'] as number) || 0
      state.title = (obj['title'] as string) || ''
      state.bufferedPositionMs = (obj['buffered_position_ms'] as number) || 0
      state.audioOutput = (obj['audio_output'] as string) || 'speaker'
      state.selectedDevice = (obj['selected_device'] as string) || ''
      this.onStateUpdate?.(state)
    } catch (e) {
      console.error(TAG, 'Parse state sync failed: ' + (e as BusinessError).message)
    }
  }

  /**
   * 解析命令结果 JSON
   */
  private parseCommandResult(jsonStr: string): void {
    try {
      let obj = JSON.parse(jsonStr) as Record<string, Object>
      let result = new CommandResult()
      result.action = (obj['action'] as string) || ''
      result.success = (obj['success'] as boolean) || false
      result.positionMs = (obj['position_ms'] as number) || 0
      result.output = (obj['output'] as string) || ''
      result.error = (obj['error'] as string) || ''
      this.onCommandResult?.(result)
    } catch (e) {
      console.error(TAG, 'Parse command result failed: ' + (e as BusinessError).message)
    }
  }

  /**
   * 解析输出变化 JSON
   */
  private parseOutputChanged(jsonStr: string): void {
    try {
      let obj = JSON.parse(jsonStr) as Record<string, Object>
      let output = (obj['output'] as string) || 'speaker'
      console.info(TAG, 'Audio output changed: ' + output)
      this.onOutputChanged?.(output)
    } catch (e) {
      console.error(TAG, 'Parse output changed failed: ' + (e as BusinessError).message)
    }
  }

  /**
   * ArrayBuffer 转字符串
   */
  private arrayBufferToString(buffer: ArrayBuffer): string {
    let view = new DataView(buffer)
    let str = ''
    for (let i = 0; i < view.byteLength; ++i) {
      str += String.fromCharCode(view.getUint8(i))
    }
    return str
  }

  // ==================== 发送命令 ====================

  /**
   * 发送控制命令（JSON 帧）
   */
  private async sendCommand(payload: CommandPayload): Promise<void> {
    if (!this.tcpSocket || !this.isConnected) return

    let jsonStr = JSON.stringify(payload)
    let textEncoder = util.TextEncoder.create()
    let jsonBytes = textEncoder.encodeInto(jsonStr)
    // 帧格式：[0x20] [4B JSON长度] [JSON数据]
    let frame = new ArrayBuffer(1 + 4 + jsonBytes.length)
    let view = new DataView(frame)
    view.setUint8(0, FRAME_TYPE_COMMAND)
    view.setInt32(1, jsonBytes.length)
    let uint8View = new Uint8Array(frame)
    uint8View.set(jsonBytes, 5)

    try {
      await this.tcpSocket.send({ data: frame } as socket.TCPSendOptions)
    } catch (e) {
      console.error(TAG, 'Send command failed: ' + (e as BusinessError).message)
    }
  }

  /**
   * 发送握手确认
   */
  async sendHelloAck(deviceName: string): Promise<void> {
    await this.sendCommand({
      type: 'hello_ack',
      device_name: deviceName,
      platform: PLATFORM,
      version: PROTOCOL_VERSION.toString()
    })
  }

  async sendPlay(): Promise<void> {
    await this.sendCommand({ type: 'command', action: 'play' })
  }

  async sendPause(): Promise<void> {
    await this.sendCommand({ type: 'command', action: 'pause' })
  }

  async sendStop(): Promise<void> {
    await this.sendCommand({ type: 'command', action: 'stop' })
  }

  async sendSeek(positionMs: number): Promise<void> {
    await this.sendCommand({ type: 'command', action: 'seek', position_ms: positionMs.toString() })
  }

  async sendSetAudioOutput(output: string): Promise<void> {
    await this.sendCommand({ type: 'command', action: 'set_audio_output', output: output })
  }

  async sendGetState(): Promise<void> {
    await this.sendCommand({ type: 'command', action: 'get_state' })
  }

  async sendDisconnect(): Promise<void> {
    await this.sendCommand({ type: 'command', action: 'disconnect' })
  }

  // ==================== 心跳 ====================

  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      this.sendHeartbeat()
    }, HEARTBEAT_INTERVAL_MS) as number
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer !== -1) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = -1
    }
  }

  private async sendHeartbeat(): Promise<void> {
    if (!this.tcpSocket || !this.isConnected) return
    let frame = new ArrayBuffer(1)
    new DataView(frame).setUint8(0, FRAME_TYPE_HEARTBEAT)
    try {
      await this.tcpSocket.send({ data: frame } as socket.TCPSendOptions)
    } catch (e) {
      console.error(TAG, 'Send heartbeat failed: ' + (e as BusinessError).message)
    }
  }

  // ==================== 缓冲区状态报告 ====================

  /**
   * 设置当前缓冲区大小（由 AudioPlayer 调用）
   */
  updateBufferSize(currentSize: number, maxSize: number = 176400 * 6): void {
    this.currentBufferSize = currentSize
    this.maxBufferSize = maxSize
  }

  private startBufferStatusUpdates(): void {
    this.stopBufferStatusUpdates()
    this.bufferStatusTimer = setInterval(() => {
      this.sendBufferStatus()
    }, BUFFER_STATUS_INTERVAL_MS) as number
  }

  private stopBufferStatusUpdates(): void {
    if (this.bufferStatusTimer !== -1) {
      clearInterval(this.bufferStatusTimer)
      this.bufferStatusTimer = -1
    }
  }

  /**
   * 发送缓冲区状态给车机（用于流量控制）
   * 帧格式: [0x21] [4B 当前缓冲区大小] [4B 最大缓冲区大小]
   */
  private async sendBufferStatus(): Promise<void> {
    if (!this.tcpSocket || !this.isConnected) return
    let frame = new ArrayBuffer(9)
    let view = new DataView(frame)
    view.setUint8(0, FRAME_TYPE_BUFFER_STATUS)
    view.setInt32(1, this.currentBufferSize)
    view.setInt32(5, this.maxBufferSize)
    try {
      await this.tcpSocket.send({ data: frame } as socket.TCPSendOptions)
    } catch (e) {
      console.error(TAG, 'Send buffer status failed: ' + (e as BusinessError).message)
    }
  }

  // ==================== 生命周期 ====================

  private handleDisconnect(): void {
    if (!this.isConnected) return
    this.isConnected = false
    this.stopHeartbeat()
    this.stopBufferStatusUpdates()
    this.recvBuffer = new ArrayBuffer(0)
    this.onConnectionChanged?.(ConnectionState.DISCONNECTED)
  }

  async disconnect(): Promise<void> {
    this.stopHeartbeat()
    this.stopBufferStatusUpdates()
    if (this.tcpSocket) {
      try {
        this.tcpSocket.off('message')
        this.tcpSocket.off('connect')
        this.tcpSocket.off('close')
        this.tcpSocket.off('error')
        await this.tcpSocket.close()
      } catch (e) {
        // 忽略关闭错误
      }
      this.tcpSocket = null
    }
    this.isConnected = false
    this.recvBuffer = new ArrayBuffer(0)
    this.onConnectionChanged?.(ConnectionState.DISCONNECTED)
  }

  getConnectionState(): boolean {
    return this.isConnected
  }

  getServerIp(): string {
    return this.serverIp
  }
}
