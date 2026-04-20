/**
 * WebSocket服务（简化版）
 * 负责与车机端WebSocket服务器通信
 * 移除DLNA相关功能，专注于播放控制
 */
import { webSocket } from '@kit.NetworkKit'
import { BusinessError } from '@kit.PerformanceAnalysisKit'
import { Logger } from '../utils/Logger'

const TAG = 'WebSocketService'
const logger = new Logger(TAG, 0x0000)

/**
 * WebSocket消息接口
 */
export interface WebSocketMessage {
  type: string
  data?: Record<string, Object>
  action?: string
  timestamp?: number
}

/**
 * 播放命令数据
 */
export interface PlayCommandData {
  uri: string
  headers: Record<string, string>
}

/**
 * 播放并跳转命令数据
 */
export interface PlayAndSeekData {
  uri: string
  headers: Record<string, string>
  position: number
}

/**
 * 进度更新数据
 */
export interface ProgressData {
  position: number
  duration: number
  isPlaying: boolean
}

/**
 * WebSocket服务回调
 */
export interface WebSocketCallbacks {
  onConnected?: () => void
  onDisconnected?: () => void
  onConnectionError?: (error: string) => void
  onPlay?: (uri: string, headers: Record<string, string>) => void
  onPlayAndSeek?: (uri: string, headers: Record<string, string>, position: number) => void
  onResume?: () => void
  onPause?: () => void
  onStop?: () => void
  onSeek?: (position: number) => void
  onProgress?: (position: number, duration: number, isPlaying: boolean) => void
}

export class WebSocketService {
  private ws: webSocket.WebSocket | null = null
  private serverUrl: string = ''
  private isConnected: boolean = false
  private isConnecting: boolean = false

  // 心跳定时器
  private heartbeatTimer: number = -1
  private readonly HEARTBEAT_INTERVAL = 30000  // 30秒

  // 重连机制
  private reconnectTimer: number = -1
  private reconnectAttempts: number = 0
  private readonly MAX_RECONNECT_ATTEMPTS = 10
  private readonly BASE_RECONNECT_DELAY = 2000  // 2秒
  private readonly MAX_RECONNECT_DELAY = 60000   // 60秒

  // 回调
  private callbacks: WebSocketCallbacks = {}

  constructor() {
    logger.info('WebSocketService initialized')
  }

  /**
   * 设置回调
   */
  setCallbacks(callbacks: WebSocketCallbacks): void {
    this.callbacks = { ...this.callbacks, ...callbacks }
  }

  /**
   * 连接到车机端
   */
  async connect(carIp: string, port: number = 9999): Promise<void> {
    if (this.isConnected || this.isConnecting) {
      logger.warn('Already connected or connecting')
      return
    }

    this.serverUrl = `ws://${carIp}:${port}`
    this.isConnecting = true

    logger.infoWithDivider(`Connecting to ${this.serverUrl}`)

    return new Promise<void>((resolve, reject) => {
      try {
        // 创建WebSocket
        this.ws = webSocket.createWebSocket()

        // 连接成功
        this.ws.on('open', () => {
          this.isConnected = true
          this.isConnecting = false
          this.reconnectAttempts = 0

          logger.infoWithDivider('WebSocket connected')

          // 发送连接成功消息
          this.sendMessage({
            type: 'connected',
            data: {
              deviceType: 'harmonyos_phone',
              timestamp: Date.now()
            }
          })

          // 启动心跳
          this.startHeartbeat()

          // 通知连接成功
          this.callbacks.onConnected?.()
          resolve()
        })

        // 接收消息
        this.ws.on('message', (err: BusinessError, value: string | ArrayBuffer) => {
          if (!err && value) {
            this.handleMessage(value as string)
          }
        })

        // 连接关闭
        this.ws.on('close', () => {
          logger.warnWithDivider('WebSocket disconnected')
          this.isConnected = false
          this.isConnecting = false

          // 停止心跳
          this.stopHeartbeat()

          // 通知断开连接
          this.callbacks.onDisconnected?.()

          // 尝试重连
          this.scheduleReconnect()
        })

        // 错误处理
        this.ws.on('error', (err: BusinessError) => {
          logger.error(`WebSocket error: ${err.code} - ${err.name}`)

          this.isConnected = false
          this.isConnecting = false

          this.callbacks.onConnectionError?.(err.name)
          this.scheduleReconnect()
        })

        // 发起连接
        this.ws.connect(this.serverUrl, (err: BusinessError) => {
          if (err) {
            logger.error(`Connection failed: ${err.code} - ${err.name}`)
            this.isConnecting = false
            reject(new Error(err.name))
          }
        })

      } catch (error) {
        logger.error(`Connect error: ${JSON.stringify(error)}`)
        this.isConnecting = false
        reject(error)
      }
    })
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    logger.info('Disconnecting WebSocket...')

    // 停止心跳和重连
    this.stopHeartbeat()
    this.stopReconnect()

    // 关闭WebSocket
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }

    this.isConnected = false
    this.isConnecting = false
  }

  /**
   * 发送消息
   */
  private sendMessage(message: WebSocketMessage): void {
    if (!this.ws || !this.isConnected) {
      logger.warn('Cannot send message: not connected')
      return
    }

    try {
      this.ws.send(JSON.stringify(message))
    } catch (error) {
      logger.error(`Send message failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 发送状态更新
   */
  sendStateUpdate(position: number, duration: number, isPlaying: boolean): void {
    this.sendMessage({
      type: 'state_update',
      data: {
        position,
        duration,
        isPlaying,
        timestamp: Date.now()
      }
    })
  }

  /**
   * 发送控制命令（双向同步用）
   */
  sendControlCommand(action: string, data?: Record<string, Object>): void {
    this.sendMessage({
      type: 'command',
      action,
      data: data || {},
      timestamp: Date.now()
    })
  }

  /**
   * 处理接收到的消息
   */
  private handleMessage(message: string): void {
    try {
      const msg: WebSocketMessage = JSON.parse(message)
      logger.debug(`Received message: ${msg.type}`)

      switch (msg.type) {
        case 'command':
          this.handleCommand(msg)
          break

        default:
          logger.warn(`Unknown message type: ${msg.type}`)
      }
    } catch (error) {
      logger.error(`Handle message failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 处理命令消息
   */
  private handleCommand(msg: WebSocketMessage): void {
    const action = msg.action
    const data = msg.data || {}

    logger.info(`Command: ${action}`)

    switch (action) {
      case 'play':
        this.handlePlayCommand(data as PlayCommandData)
        break

      case 'play_and_seek':
        this.handlePlayAndSeekCommand(data as PlayAndSeekData)
        break

      case 'resume':
        this.callbacks.onResume?.()
        break

      case 'pause':
        this.callbacks.onPause?.()
        break

      case 'stop':
        this.callbacks.onStop?.()
        break

      case 'seek':
        this.handleSeekCommand(data)
        break

      case 'progress':
        this.handleProgressCommand(data as ProgressData)
        break

      default:
        logger.warn(`Unknown command: ${action}`)
    }
  }

  /**
   * 处理播放命令
   */
  private handlePlayCommand(data: PlayCommandData): void {
    const { uri, headers } = data
    logger.info(`Play command: URI length = ${uri.length}`)
    this.callbacks.onPlay?.(uri, headers || {})
  }

  /**
   * 处理同步启动命令
   */
  private handlePlayAndSeekCommand(data: PlayAndSeekData): void {
    const { uri, headers, position } = data
    logger.info(`Play and seek: position = ${position}ms`)
    this.callbacks.onPlayAndSeek?.(uri, headers || {}, position)
  }

  /**
   * 处理跳转命令
   */
  private handleSeekCommand(data: Record<string, Object>): void {
    const position = data.position as number
    logger.info(`Seek command: position = ${position}ms`)
    this.callbacks.onSeek?.(position)
  }

  /**
   * 处理进度更新命令
   */
  private handleProgressCommand(data: ProgressData): void {
    const { position, duration, isPlaying } = data
    this.callbacks.onProgress?.(position, duration, isPlaying)
  }

  /**
   * 启动心跳
   */
  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      this.sendMessage({
        type: 'heartbeat',
        data: { timestamp: Date.now() }
      })
    }, this.HEARTBEAT_INTERVAL)

    logger.debug('Heartbeat started')
  }

  /**
   * 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer !== -1) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = -1
      logger.debug('Heartbeat stopped')
    }
  }

  /**
   * 安排重连
   */
  private scheduleReconnect(): void {
    // 停止现有重连
    this.stopReconnect()

    // 检查重连次数
    if (this.reconnectAttempts >= this.MAX_RECONNECT_ATTEMPTS) {
      logger.warn('Max reconnect attempts reached')
      return
    }

    // 计算延迟（指数退避）
    const delay = Math.min(
      this.BASE_RECONNECT_DELAY * Math.pow(2, this.reconnectAttempts),
      this.MAX_RECONNECT_DELAY
    )

    this.reconnectAttempts++
    logger.info(`Scheduling reconnect in ${delay}ms (attempt ${this.reconnectAttempts}/${this.MAX_RECONNECT_ATTEMPTS})`)

    this.reconnectTimer = setTimeout(() => {
      logger.info('Reconnecting...')
      // 从serverUrl提取IP和端口
      const match = this.serverUrl.match(/ws:\/\/([^:]+):(\d+)/)
      if (match) {
        const [, ip, port] = match
        this.connect(ip, parseInt(port)).catch(error => {
          logger.error(`Reconnect failed: ${JSON.stringify(error)}`)
        })
      }
    }, delay)
  }

  /**
   * 停止重连
   */
  private stopReconnect(): void {
    if (this.reconnectTimer !== -1) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = -1
    }
  }

  /**
   * 是否已连接
   */
  isConnectedToServer(): boolean {
    return this.isConnected
  }

  /**
   * 是否正在连接
   */
  isConnectingToServer(): boolean {
    return this.isConnecting
  }
}
