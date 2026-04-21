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
  // 🔧 修复内存泄漏：添加重连定时器ID
  private reconnectTimeoutId: number = -1

  // 命令循环避免：缓存已处理的命令ID（10秒TTL）
  private processedCommands: Map<string, number> = new Map()
  private commandCleanupTimer: number = -1

  // 播放回调
  private onPlayCommandCallback: ((uri: string, httpHeaders: Record<string, string>) => void) | null = null
  private onPlayAndSeekCallback: ((uri: string, httpHeaders: Record<string, string>, position: number) => void) | null = null
  private onResumeCommandCallback: (() => void) | null = null
  private onPauseCommandCallback: (() => void) | null = null
  private onStopCommandCallback: (() => void) | null = null
  private onSeekCommandCallback: ((position: number) => void) | null = null
  // ⏱️ 时间戳对齐：添加sendTime参数
  private onProgressUpdateCallback: ((position: number, duration: number, isPlaying: boolean, sendTime: number) => void) | null = null
  private onDisconnectedCallback: (() => void) | null = null  // 连接断开回调
  private onConnectedCallback: (() => void) | null = null  // 连接成功回调
  private onConnectionStatusChangeCallback: ((isConnected: boolean) => void) | null = null  // 连接状态变化回调
  // ⏱️ 时间校准：服务器时间回调（用于计算系统时间偏移）
  private onServerTimeCallback: ((serverTime: number) => void) | null = null

  /**
   * 连接到车机端
   */
  async connect(carIp: string): Promise<void> {
    if (this.isConnected) {
      hilog.warn(DOMAIN, TAG, '已经连接到车机端')
      return
    }

    this.carServerUrl = `ws://${carIp}:9999`

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

          // 启动命令缓存清理定时器
          this.startCommandCleanup()

          // 通知连接成功
          this.onConnectedCallback?.()

          // 通知连接状态变化
          this.onConnectionStatusChangeCallback?.(true)
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
        hilog.warn(DOMAIN, TAG, '========================================')
        hilog.warn(DOMAIN, TAG, 'WebSocket连接已关闭!')
        hilog.warn(DOMAIN, TAG, `关闭码: ${value.code}, 原因: ${value.reason}`)
        hilog.warn(DOMAIN, TAG, '========================================')

        // 停止心跳
        this.stopHeartbeat()

        // 通知连接断开（暂停视频）
        this.onDisconnectedCallback?.()

        // 通知连接状态变化
        this.onConnectionStatusChangeCallback?.(false)

        hilog.info(DOMAIN, TAG, '已通知连接断开，视频应已暂停')

        // 尝试重连
        this.scheduleReconnect()
      })

      // 监听错误事件
      this.ws.on('error', (err: BusinessError) => {
        hilog.error(DOMAIN, TAG, `WebSocket错误: ${err.code} - ${err.name}`)
        // WebSocket错误通常意味着连接断开，通知暂停视频
        if (this.isConnected) {
          this.isConnected = false
          this.onDisconnectedCallback?.()

          // 通知连接状态变化
          this.onConnectionStatusChangeCallback?.(false)

          hilog.info(DOMAIN, TAG, 'WebSocket错误，已通知连接断开')
        }
      })

      // 连接到服务器
      this.ws.connect(this.carServerUrl, (err: BusinessError, value: boolean) => {
        if (!err) {
          hilog.info(DOMAIN, TAG, `WebSocket连接请求已发送: ${this.carServerUrl}`)
        } else {
          hilog.error(DOMAIN, TAG, `WebSocket连接请求失败: ${err.code} - ${err.name}`)
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
          // ⏱️ 时间校准：提取服务器时间戳用于计算系统时间偏移
          hilog.info(DOMAIN, TAG, '收到欢迎消息')
          if (data) {
            const serverTime = (data as Record<string, Object>)['timestamp'] as number
            if (serverTime) {
              hilog.info(DOMAIN, TAG, `⏱️ 服务器时间戳: ${serverTime}`)
              this.onServerTimeCallback?.(serverTime)
            } else {
              hilog.warn(DOMAIN, TAG, 'welcome消息中缺少timestamp字段')
            }
          } else {
            hilog.warn(DOMAIN, TAG, 'welcome消息中缺少data字段')
          }
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
          const headersValue = (data as Record<string, Object>)['headers']

          hilog.info(DOMAIN, TAG, `========================================`)
          hilog.info(DOMAIN, TAG, `收到play命令`)
          hilog.info(DOMAIN, TAG, `URI长度: ${uri.length}`)
          hilog.info(DOMAIN, TAG, `headers值类型: ${typeof headersValue}`)
          hilog.info(DOMAIN, TAG, `headers值: ${JSON.stringify(headersValue)}`)

          // 解析HTTP头 - 可能是字符串或对象
          let headers: Record<string, string> = {}

          if (headersValue !== null && headersValue !== undefined) {
            if (typeof headersValue === 'string') {
              // 情况1: headers是字符串，需要解析
              const headersStr = headersValue as string
              hilog.info(DOMAIN, TAG, `headers是字符串，尝试解析`)

              try {
                // 尝试标准JSON解析
                headers = JSON.parse(headersStr) as Record<string, string>
                hilog.info(DOMAIN, TAG, `成功从JSON解析HTTP头，数量: ${Object.keys(headers).length}`)
              } catch (e) {
                // JSON解析失败，尝试解析Java Map的toString格式
                // 格式: {key1=value1, key2=value2}
                hilog.warn(DOMAIN, TAG, `JSON解析失败，尝试解析Java Map格式`)
                try {
                  // 去掉外层的{ }
                  const inner = headersStr.trim().substring(1, headersStr.length - 1)

                  // 按 ", " 分割键值对
                  const pairs = inner.split(', ')
                  pairs.forEach((pair: string) => {
                    // 按 = 分割键和值
                    const equalIndex = pair.indexOf('=')
                    if (equalIndex > 0) {
                      const key = pair.substring(0, equalIndex).trim()
                      const value = pair.substring(equalIndex + 1).trim()
                      headers[key] = value
                    }
                  })
                  hilog.info(DOMAIN, TAG, `成功从Map格式解析HTTP头，数量: ${Object.keys(headers).length}`)
                } catch (e2) {
                  hilog.error(DOMAIN, TAG, `解析Map格式也失败: ${e2}`)
                }
              }
            } else if (typeof headersValue === 'object') {
              // 情况2: headers已经是对象，直接使用
              hilog.info(DOMAIN, TAG, `headers是对象，直接使用`)
              // 转换为Record<string, string>
              const headersObj = headersValue as Record<string, Object>
              const keys = Object.keys(headersObj)
              keys.forEach((key: string) => {
                const value = headersObj[key]
                if (typeof value === 'string') {
                  headers[key] = value as string
                } else {
                  headers[key] = JSON.stringify(value)
                }
              })
              hilog.info(DOMAIN, TAG, `成功从对象转换HTTP头，数量: ${Object.keys(headers).length}`)
            } else {
              hilog.warn(DOMAIN, TAG, `headers类型未知: ${typeof headersValue}`)
            }
          }

          hilog.info(DOMAIN, TAG, `最终HTTP头数量: ${Object.keys(headers).length}`)
          if (Object.keys(headers).length > 0) {
            hilog.info(DOMAIN, TAG, `HTTP头内容: ${JSON.stringify(headers).substring(0, 300)}`)
          }
          hilog.info(DOMAIN, TAG, `========================================`)

          this.onPlayCommandCallback?.(uri, headers)
        }
        break

      case 'play_and_seek':
        // 新的同步启动命令：加载视频、跳到指定进度、暂停等待
        if (data) {
          const dataRecord = data as Record<string, Object>
          const uri = dataRecord['uri'] as string
          const headersValue = dataRecord['headers']
          const position = dataRecord['position'] as number

          hilog.info(DOMAIN, TAG, `========================================`)
          hilog.info(DOMAIN, TAG, `收到play_and_seek命令（同步启动）`)
          hilog.info(DOMAIN, TAG, `URI长度: ${uri.length}`)
          hilog.info(DOMAIN, TAG, `目标进度: ${position}ms`)

          // 解析HTTP头（与play命令相同的逻辑）
          let headers: Record<string, string> = {}
          if (headersValue !== null && headersValue !== undefined) {
            if (typeof headersValue === 'string') {
              try {
                headers = JSON.parse(headersValue as string) as Record<string, string>
              } catch (e) {
                // 尝试解析Java Map格式
                const inner = (headersValue as string).trim().substring(1, (headersValue as string).length - 1)
                const pairs = inner.split(', ')
                pairs.forEach((pair: string) => {
                  const equalIndex = pair.indexOf('=')
                  if (equalIndex > 0) {
                    const key = pair.substring(0, equalIndex).trim()
                    const value = pair.substring(equalIndex + 1).trim()
                    headers[key] = value
                  }
                })
              }
            } else if (typeof headersValue === 'object') {
              const headersObj = headersValue as Record<string, Object>
              Object.keys(headersObj).forEach((key: string) => {
                const value = headersObj[key]
                headers[key] = (typeof value === 'string') ? (value as string) : JSON.stringify(value)
              })
            }
          }

          hilog.info(DOMAIN, TAG, `HTTP头数量: ${Object.keys(headers).length}`)
          hilog.info(DOMAIN, TAG, `========================================`)

          this.onPlayAndSeekCallback?.(uri, headers, position)
        }
        break

      case 'resume':
        // 恢复播放命令：用于同步启动流程
        hilog.info(DOMAIN, TAG, `收到resume命令（开始同步播放）`)
        this.onResumeCommandCallback?.()
        break

      case 'pause':
        hilog.info(DOMAIN, TAG, `收到pause命令，调用回调`)
        this.onPauseCommandCallback?.()
        break

      case 'stop':
        hilog.info(DOMAIN, TAG, `收到stop命令，调用回调`)
        this.onStopCommandCallback?.()
        break

      case 'seek':
        hilog.info(DOMAIN, TAG, `收到seek命令`)
        if (data) {
          const position = (data as Record<string, Object>)['position'] as number
          hilog.info(DOMAIN, TAG, `seek位置: ${position}ms`)
          this.onSeekCommandCallback?.(position)
        }
        break

      case 'progress':
        // ⏱️ 时间戳对齐：传递sendTime字段
        if (data) {
          const dataRecord = data as Record<string, Object>
          const pos = dataRecord['position'] as number
          const dur = dataRecord['duration'] as number
          const playing = dataRecord['isPlaying'] as boolean
          const sendTime = dataRecord['sendTime'] as number || Date.now()  // 兼容旧版本
          this.onProgressUpdateCallback?.(pos, dur, playing, sendTime)
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
   * 发送控制命令到车机端（用于双向同步）
   * @param action 命令类型（play, pause, stop, seek, switch_output）
   * @param data 命令数据（可选）
   */
  sendControlCommand(action: string, data?: Record<string, Object>): void {
    const syncId = this.generateSyncId()

    // 创建data对象，确保有默认值
    const controlData: Record<string, Object> = data || {} as Record<string, Object>

    // 添加syncId和source
    controlData['syncId'] = syncId
    controlData['source'] = 'phone'

    const message: WebSocketMessage = {
      type: 'control_command',
      action: action,
      data: controlData,
      timestamp: Date.now()
    }

    hilog.info(DOMAIN, TAG, `发送控制命令到车机端: action=${action}, syncId=${syncId}`)
    this.sendMessage(message)

    // 标记命令已处理（避免循环）
    this.markCommandProcessed(syncId)
  }

  /**
   * 生成唯一的syncId
   */
  private generateSyncId(): string {
    return `phone_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`
  }

  /**
   * 检查命令是否已处理
   */
  private isCommandProcessed(syncId: string): boolean {
    const timestamp = this.processedCommands.get(syncId)
    if (!timestamp) {
      return false
    }
    // 检查是否在10秒内
    const age = Date.now() - timestamp
    return age < 10000
  }

  /**
   * 标记命令已处理
   */
  private markCommandProcessed(syncId: string): void {
    this.processedCommands.set(syncId, Date.now())
    hilog.debug(DOMAIN, TAG, `标记命令已处理: syncId=${syncId}`)
  }

  /**
   * 启动命令缓存清理定时器
   */
  private startCommandCleanup(): void {
    this.commandCleanupTimer = setInterval(() => {
      const now = Date.now()
      const expiredKeys: string[] = []

      this.processedCommands.forEach((timestamp: number, syncId: string) => {
        if (now - timestamp > 10000) { // 10秒TTL
          expiredKeys.push(syncId)
        }
      })

      expiredKeys.forEach((key: string) => {
        this.processedCommands.delete(key)
      })

      if (expiredKeys.length > 0) {
        hilog.debug(DOMAIN, TAG, `清理了${expiredKeys.length}个过期命令缓存`)
      }
    }, 10000) // 每10秒清理一次
  }

  /**
   * 停止命令缓存清理定时器
   */
  private stopCommandCleanup(): void {
    if (this.commandCleanupTimer !== -1) {
      clearInterval(this.commandCleanupTimer)
      this.commandCleanupTimer = -1
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
    // 🔧 修复内存泄漏：保存定时器ID以便清理
    this.reconnectTimeoutId = setTimeout(() => {
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
    this.stopCommandCleanup()

    // 清空命令缓存
    this.processedCommands.clear()

    // 🔧 修复内存泄漏：清理重连定时器
    if (this.reconnectTimeoutId !== -1) {
      clearTimeout(this.reconnectTimeoutId)
      this.reconnectTimeoutId = -1
    }

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

  /**
   * 设置播放并跳转命令回调（用于同步启动）
   */
  setOnPlayAndSeek(callback: (uri: string, httpHeaders: Record<string, string>, position: number) => void): void {
    this.onPlayAndSeekCallback = callback
  }

  /**
   * 设置恢复播放命令回调（用于同步启动）
   */
  setOnResumeCommand(callback: () => void): void {
    this.onResumeCommandCallback = callback
  }

  /**
   * 设置连接断开回调
   */
  setOnDisconnected(callback: () => void): void {
    this.onDisconnectedCallback = callback
  }

  /**
   * 设置连接成功回调
   */
  setOnConnected(callback: () => void): void {
    this.onConnectedCallback = callback
  }

  /**
   * 设置连接状态变化回调
   */
  setOnConnectionStatusChange(callback: (isConnected: boolean) => void): void {
    this.onConnectionStatusChangeCallback = callback
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

  // ⏱️ 时间戳对齐：添加sendTime参数到setter
  setOnProgressUpdate(callback: (position: number, duration: number, isPlaying: boolean, sendTime: number) => void): void {
    this.onProgressUpdateCallback = callback
  }

  // ⏱️ 时间校准：设置服务器时间回调
  setOnServerTime(callback: (serverTime: number) => void): void {
    this.onServerTimeCallback = callback
  }
}
