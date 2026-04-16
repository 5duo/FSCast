/**
 * 车机WebSocket客户端
 * 鸿蒙手机端 - 连接到车机端WebSocket服务器
 * 接收播放命令并控制视频播放
 */
import { webSocket } from '@kit.NetworkKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { BusinessError } from '@kit.BasicServicesKit'

const DOMAIN = 0x0000
const TAG = 'CarWebSocketClient'

// 定义消息接口
interface WebSocketMessage {
  type: string
  data?: Object
  action?: string
  timestamp?: number
}

// 定义数据接口
interface ConnectedData {
  deviceType: string
  timestamp: number
}

interface HeartbeatData {
  timestamp: number
}

interface StateUpdateData {
  position: number
  duration: number
  isPlaying: boolean
  timestamp: number
}

interface ErrorData {
  message: string
  timestamp: number
}

export class CarWebSocketClient {
  private ws: webSocket.WebSocket | null = null
  private carServerUrl: string = ''
  private isConnected: boolean = false
  private reconnectTimer: number = -1

  // 播放回调
  private onPlayCommandCallback: ((uri: string, httpHeaders: Record<string, string>) => void) | null = null
  private onPauseCommandCallback: (() => void) | null = null
  private onStopCommandCallback: (() => void) | null = null
  private onSeekCommandCallback: ((position: number) => void) | null = null
  private onProgressUpdateCallback: ((position: number, duration: number, isPlaying: boolean) => void) | null = null

  /**
   * 连接到车机端
   */
  async connect(carIp: string): Promise<void> {
    if (this.isConnected) {
      hilog.warn(DOMAIN, TAG, '已经连接到车机端')
      return
    }

    this.carServerUrl = `ws://${carIp}:8080`

    try {
      // 创建WebSocket连接
      this.ws = webSocket.createWebSocket()

      // 监听连接打开事件
      this.ws.on('open', (err: BusinessError, value: Object) => {
        if (!err) {
          this.isConnected = true
          hilog.info(DOMAIN, TAG, '已连接到车机端')

          // 发送连接成功消息
          const connectedData: ConnectedData = {
            deviceType: 'harmonyos_phone',
            timestamp: Date.now()
          }
          this.sendMessage({
            type: 'connected',
            data: connectedData
          })

          // 启动心跳
          this.startHeartbeat()
        } else {
          hilog.error(DOMAIN, TAG, '连接打开失败')
        }
      })

      // 监听消息接收事件
      this.ws.on('message', (err: BusinessError, value: string | ArrayBuffer) => {
        if (!err && value) {
          this.handleMessage(value as string)
        }
      })

      // 监听连接关闭事件
      this.ws.on('close', (err: BusinessError, value: webSocket.CloseResult) => {
        this.isConnected = false
        hilog.info(DOMAIN, TAG, '连接已关闭: ' + value.code)

        // 停止心跳
        this.stopHeartbeat()

        // 尝试重连
        this.scheduleReconnect()
      })

      // 监听错误事件
      this.ws.on('error', (err: BusinessError) => {
        hilog.error(DOMAIN, TAG, 'WebSocket错误')
      })

      // 连接到服务器
      this.ws.connect(this.carServerUrl, (err: BusinessError, value: boolean) => {
        if (!err) {
          hilog.info(DOMAIN, TAG, 'WebSocket连接请求已发送')
        } else {
          hilog.error(DOMAIN, TAG, 'WebSocket连接请求失败')
          this.scheduleReconnect()
        }
      })

    } catch (error) {
      hilog.error(DOMAIN, TAG, '连接车机端失败')
      this.scheduleReconnect()
    }
  }

  /**
   * 处理收到的消息
   */
  private handleMessage(message: string): void {
    try {
      const json: WebSocketMessage = JSON.parse(message) as WebSocketMessage
      const type: string = json.type
      const data: Object | undefined = json.data

      hilog.debug(DOMAIN, TAG, '收到消息: ' + type)

      switch (type) {
        case 'command':
          if (json.action) {
            this.handleCommand(json.action, data)
          }
          break

        case 'welcome':
          hilog.info(DOMAIN, TAG, '收到欢迎消息')
          break

        default:
          hilog.warn(DOMAIN, TAG, '未知消息类型: ' + type)
      }

    } catch (error) {
      hilog.error(DOMAIN, TAG, '解析消息失败')
    }
  }

  /**
   * 处理命令
   */
  private handleCommand(action: string, data: Object | undefined): void {
    hilog.info(DOMAIN, TAG, '收到命令: ' + action)

    switch (action) {
      case 'play':
        if (data) {
          const uri = (data as Record<string, Object>)['uri'] as string
          const headers = (data as Record<string, Object>)['headers'] as Record<string, string>
          this.onPlayCommandCallback?.(uri, headers)
        }
        break

      case 'pause':
        this.onPauseCommandCallback?.()
        break

      case 'stop':
        this.onStopCommandCallback?.()
        break

      case 'seek':
        if (data) {
          const position = (data as Record<string, Object>)['position'] as number
          this.onSeekCommandCallback?.(position)
        }
        break

      case 'progress':
        if (data) {
          const dataRecord = data as Record<string, Object>
          const pos = dataRecord['position'] as number
          const dur = dataRecord['duration'] as number
          const playing = dataRecord['isPlaying'] as boolean
          this.onProgressUpdateCallback?.(pos, dur, playing)
        }
        break

      default:
        hilog.warn(DOMAIN, TAG, '未知命令: ' + action)
    }
  }

  /**
   * 发送消息到车机端
   */
  private sendMessage(message: WebSocketMessage): void {
    if (!this.isConnected || !this.ws) {
      hilog.warn(DOMAIN, TAG, '未连接到车机端，无法发送消息')
      return
    }

    try {
      const messageStr: string = JSON.stringify(message)
      this.ws.send(messageStr)
      hilog.debug(DOMAIN, TAG, '发送消息')
    } catch (error) {
      hilog.error(DOMAIN, TAG, '发送消息失败')
    }
  }

  /**
   * 发送状态更新到车机端
   */
  sendStateUpdate(position: number, duration: number, isPlaying: boolean): void {
    const data: StateUpdateData = {
      position: position,
      duration: duration,
      isPlaying: isPlaying,
      timestamp: Date.now()
    }
    this.sendMessage({
      type: 'state_update',
      data: data
    })
  }

  /**
   * 发送错误报告到车机端
   */
  sendError(message: string): void {
    const data: ErrorData = {
      message: message,
      timestamp: Date.now()
    }
    this.sendMessage({
      type: 'error',
      data: data
    })
  }

  /**
   * 启动心跳
   */
  private startHeartbeat(): void {
    // 每30秒发送一次心跳
    this.reconnectTimer = setInterval(() => {
      if (this.isConnected) {
        const data: HeartbeatData = {
          timestamp: Date.now()
        }
        this.sendMessage({
          type: 'heartbeat',
          data: data
        })
      }
    }, 30000)
  }

  /**
   * 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.reconnectTimer !== -1) {
      clearInterval(this.reconnectTimer)
      this.reconnectTimer = -1
    }
  }

  /**
   * 安排重连
   */
  private scheduleReconnect(): void {
    // 5秒后重连
    setTimeout(() => {
      if (!this.isConnected && this.carServerUrl) {
        const ip: string = this.carServerUrl.replace('ws://', '').split(':')[0]
        hilog.info(DOMAIN, TAG, '尝试重新连接到车机端')
        this.connect(ip)
      }
    }, 5000)
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    hilog.info(DOMAIN, TAG, '断开连接')

    this.isConnected = false
    this.stopHeartbeat()

    if (this.ws) {
      try {
        this.ws.close((err: BusinessError, value: boolean) => {
          if (!err) {
            hilog.info(DOMAIN, TAG, 'WebSocket已关闭')
          }
        })
      } catch (error) {
        hilog.error(DOMAIN, TAG, '关闭WebSocket失败')
      }

      this.ws = null
    }
  }

  /**
   * 检查是否已连接
   */
  isClientConnected(): boolean {
    return this.isConnected
  }

  /**
   * 设置播放命令回调
   */
  setOnPlayCommand(callback: (uri: string, httpHeaders: Record<string, string>) => void): void {
    this.onPlayCommandCallback = callback
  }

  setOnPauseCommand(callback: () => void): void {
    this.onPauseCommandCallback = callback
  }

  setOnStopCommand(callback: () => void): void {
    this.onStopCommandCallback = callback
  }

  setOnSeekCommand(callback: (position: number) => void): void {
    this.onSeekCommandCallback = callback
  }

  setOnProgressUpdate(callback: (position: number, duration: number, isPlaying: boolean) => void): void {
    this.onProgressUpdateCallback = callback
  }
}
