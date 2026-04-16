/**
 * 同步客户端
 * 鸿蒙手机端 - 连接车机SyncServer，接收播放状态同步
 */
import { socket } from '@kit.NetworkKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0000
const TAG = 'SyncClient'
const SYNC_PORT = 19890  // 与车机SyncServer端口一致

export class SyncClient {
  private tcpSocket: socket.TCPSocket | null = null
  private isConnected: boolean = false
  private carIpAddress: string = ''

  // 播放状态
  private isPlaying: boolean = false
  private currentPosition: number = 0
  private duration: number = 0

  // 回调
  private onPlayCommand: (() => void) | null = null
  private onPauseCommand: (() => void) | null = null
  private onStopCommand: (() => void) | null = null
  private onSeekCommand: ((position: number) => void) | null = null
  private onProgressUpdate: ((position: number, duration: number, isPlaying: boolean) => void) | null = null

  /**
   * 连接到车机同步服务器
   */
  async connect(carIpAddress: string): Promise<void> {
    if (this.isConnected) {
      hilog.warn(DOMAIN, TAG, '已经连接到车机')
      return
    }

    try {
      this.carIpAddress = carIpAddress

      // 创建TCP套接字
      this.tcpSocket = socket.constructTCPSocketInstance()

      // 订阅事件
      this.tcpSocket.on('message', (value: socket.SocketMessageInfo) => {
        const message = this.arrayBufferToString(value.message)
        this.handleSyncMessage(message)
      })

      this.tcpSocket.on('connect', () => {
        hilog.info(DOMAIN, TAG, `已连接到车机同步服务器: ${carIpAddress}:${SYNC_PORT}`)
        this.isConnected = true
        this.sendDeviceInfo()
      })

      this.tcpSocket.on('close', () => {
        hilog.info(DOMAIN, TAG, '与车机断开连接')
        this.isConnected = false
      })

      this.tcpSocket.on('error', (err: BusinessError) => {
        hilog.error(DOMAIN, TAG, `TCP error: ${err.message}`)
        this.isConnected = false
      })

      // 连接到车机同步服务器
      const connectOptions: socket.TCPConnectOptions = {
        address: {
          address: carIpAddress,
          port: SYNC_PORT
        },
        timeout: 5000
      }
      await this.tcpSocket.connect(connectOptions)

    } catch (error) {
      hilog.error(DOMAIN, TAG, `连接车机同步服务器失败: ${(error as BusinessError).message}`)
      this.isConnected = false
      throw new Error(`连接车机同步服务器失败: ${(error as BusinessError).message}`)
    }
  }

  /**
   * ArrayBuffer转字符串
   */
  private arrayBufferToString(buffer: ArrayBuffer): string {
    const dataView = new DataView(buffer)
    let str = ''
    for (let i = 0; i < dataView.byteLength; ++i) {
      str += String.fromCharCode(dataView.getUint8(i))
    }
    return str
  }

  /**
   * 处理同步消息
   */
  private handleSyncMessage(message: string): void {
    try {
      const json = JSON.parse(message) as Record<string, Object>
      const type = json['type'] as string

      hilog.debug(DOMAIN, TAG, `收到同步消息: ${type}`)

      switch (type) {
        case 'play':
          this.onPlayCommand?.()
          break

        case 'pause':
          this.onPauseCommand?.()
          break

        case 'seek':
          const position = (json['position'] as number) || 0
          this.onSeekCommand?.(position)
          break

        case 'stop':
          this.onStopCommand?.()
          break

        case 'progress':
          const pos = (json['position'] as number) || 0
          const dur = (json['duration'] as number) || 0
          const playing = (json['isPlaying'] as boolean) || false
          const timestamp = (json['timestamp'] as number) || 0

          // 计算网络延迟并补偿播放位置
          const networkLatency = Date.now() - timestamp
          const compensatedPosition = pos + networkLatency

          // 更新播放状态
          this.isPlaying = playing
          this.currentPosition = compensatedPosition
          this.duration = dur

          // 通知UI更新
          this.onProgressUpdate?.(compensatedPosition, dur, playing)

          break

        case 'device_info_ack':
          hilog.info(DOMAIN, TAG, `车机设备信息: ${JSON.stringify(json)}`)
          break

        default:
          hilog.warn(DOMAIN, TAG, `未知同步消息类型: ${type}`)
      }

    } catch (error) {
      hilog.error(DOMAIN, TAG, `解析同步消息失败: ${message}, error: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 发送设备信息
   */
  private async sendDeviceInfo(): Promise<void> {
    if (!this.isConnected || !this.tcpSocket) {
      return
    }

    try {
      const message = JSON.stringify({
        type: 'device_info',
        deviceId: 'phone_harmony',
        deviceName: 'FSCast Remote'
      })

      const sendOptions: socket.TCPSendOptions = {
        data: message
      }
      await this.tcpSocket.send(sendOptions)
      hilog.info(DOMAIN, TAG, '已发送设备信息到车机')

    } catch (error) {
      hilog.error(DOMAIN, TAG, `发送设备信息失败: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 断开连接
   */
  async disconnect(): Promise<void> {
    hilog.info(DOMAIN, TAG, '正在断开与车机的连接...')

    this.isConnected = false

    if (this.tcpSocket) {
      try {
        this.tcpSocket.off('message')
        this.tcpSocket.off('connect')
        this.tcpSocket.off('close')
        this.tcpSocket.off('error')
        await this.tcpSocket.close()
      } catch (error) {
        hilog.error(DOMAIN, TAG, `关闭TCP连接失败: ${(error as BusinessError).message}`)
      }
      this.tcpSocket = null
    }

    hilog.info(DOMAIN, TAG, '已断开与车机的连接')
  }

  /**
   * 设置播放命令回调
   */
  setOnPlayCommand(callback: (() => void) | null): void {
    this.onPlayCommand = callback
  }

  setOnPauseCommand(callback: (() => void) | null): void {
    this.onPauseCommand = callback
  }

  setOnStopCommand(callback: (() => void) | null): void {
    this.onStopCommand = callback
  }

  setOnSeekCommand(callback: ((position: number) => void) | null): void {
    this.onSeekCommand = callback
  }

  setOnProgressUpdate(callback: ((position: number, duration: number, isPlaying: boolean) => void) | null): void {
    this.onProgressUpdate = callback
  }

  /**
   * 获取连接状态
   */
  getIsConnected(): boolean {
    return this.isConnected
  }
}
