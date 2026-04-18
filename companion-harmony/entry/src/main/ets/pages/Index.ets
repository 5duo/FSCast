/**
 * FSCast Remote 主页面
 * DLNA DMR + 设备发现响应 + 同步客户端
 * UI重构：控制台风格，移除播放模式选择，统一状态管理
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { preferences } from '@kit.ArkData'
import { connection } from '@kit.NetworkKit'
import { http } from '@kit.NetworkKit'
import { pasteboard } from '@kit.BasicServicesKit'
import { fileIo } from '@kit.CoreFileKit'
import { common } from '@kit.AbilityKit'
import { Environment } from '@kit.CoreFileKit'
import { CarWebSocketClient } from '../service/CarWebSocketClient'
import { VideoPlayer } from '../service/VideoPlayer'
import { MediaSessionService } from '../service/MediaSessionService'
import { BackgroundService } from '../service/BackgroundService'
import { ConnectionStatusCard } from '../components/ConnectionStatusCard'
import { VideoPlayerCard } from '../components/VideoPlayerCard'
import { VideoUrlCard } from '../components/VideoUrlCard'
// import { ControlPanel } from '../components/ControlPanel'  // 已移除，功能整合到StatusPanel
import { CarIpDialog } from '../components/CarIpDialog'
import { NetworkScanService } from '../service/NetworkScanService'

const TAG = 'IndexPage'
const DOMAIN = 0x0000

@Entry
@Component
struct Index {
  // ===== 服务实例 =====
  private videoPlayer: VideoPlayer = new VideoPlayer()
  private carWebSocketClient: CarWebSocketClient = new CarWebSocketClient()
  private mediaSessionService: MediaSessionService = new MediaSessionService()
  private backgroundService: BackgroundService = new BackgroundService()
  private networkScanService: NetworkScanService = new NetworkScanService()

  // ===== 网络连接监听 =====
  private netCon = connection.createNetConnection()

  // ===== 车机端IP地址 =====
  private carIpAddress: string = '192.168.200.47'  // 默认车机IP
  private dataPreferences?: preferences.Preferences  // 用于持久化配置

  // ===== IP设置对话框控制器 =====
  private ipDialogController?: CustomDialogController

  // ===== 状态刷新定时器 =====
  private statusCheckTimer: number = -1
  private readonly STATUS_CHECK_INTERVAL = 5000  // 5秒

  // ===== DLNA DMR状态 =====
  @State dlnaUri: string = ''  // 接收到的DLNA播放URI
  @State dlnaHttpHeaders: Record<string, string> = {}  // 接收到的HTTP头
  @State dlnaIsPlaying: boolean = false
  @State dlnaPosition: number = 0
  @State dlnaDuration: number = 0

  // ===== 主视频播放器 =====
  @State mainVideoPrepared: boolean = false  // 视频是否已准备完成
  private mainVideoController: VideoController = new VideoController()
  private lastSyncTime: number = 0  // 上次同步时间，用于防抖
  private lastSeekTime: number = 0  // 上次seek的时间戳
  @State phoneActualPosition: number = 0  // 手机端实际播放进度

  // ===== WebSocket连接状态 =====
  @State webSocketConnected: boolean = false
  @State carConnectionStatus: string = '未连接'
  @State isConnecting: boolean = false  // 正在连接中

  // ===== 新增状态变量 =====
  @State networkStatus: string = 'unknown'  // unknown, available, lost
  @State lastConnectionTime: number = 0  // 最后连接时间戳
  @State localIpAddress: string = ''  // 本机IP地址
  @State tempIp: string = ''  // IP设置对话框临时变量

  // 自动扫描状态
  @State isAutoScanning: boolean = false  // 是否正在自动扫描
  @State autoScanProgress: string = ''  // 自动扫描进度
  @State hasAttemptedAutoScan: boolean = false  // 是否已经尝试过自动扫描

  // 刷新状态
  @State isRefreshing: boolean = false  // 是否正在刷新连接
  @State scanProgressDetail: string = ''  // 详细扫描进度（显示在卡片中）

  // 下载状态
  @State isDownloading: boolean = false
  @State downloadProgress: number = 0

  // 双向同步状态：是否在手机播放模式（已收到URI并正在播放）
  @State isPhoneMode: boolean = false

  aboutToAppear(): void {
    hilog.info(DOMAIN, TAG, '========================================')
    hilog.info(DOMAIN, TAG, 'Page aboutToAppear - 开始初始化')
    hilog.info(DOMAIN, TAG, '========================================')

    // 设置媒体服务和后台服务的上下文
    this.mediaSessionService.setContext(getContext(this))
    this.backgroundService.setContext(getContext(this))

    // 初始化媒体会话（同步，快速返回）
    this.initializeMediaSession()

    // 启动定期状态检查（先启动，确保状态更新机制运行）
    this.startPeriodicStatusCheck()

    // ⚠️ 重要：异步初始化服务，不阻塞UI显示
    // UI会先显示默认状态，然后逐步更新
    setTimeout(() => {
      this.initializeServices()
    }, 100)  // 延迟100ms，确保UI已经渲染

    hilog.info(DOMAIN, TAG, 'UI初始化完成，开始异步加载服务')
  }

  /**
   * 初始化媒体会话
   */
  private async initializeMediaSession(): Promise<void> {
    try {
      // 创建并激活AVSession
      await this.mediaSessionService.createSession()

      // 设置MediaSession控制回调
      this.mediaSessionService.onPlay = () => {
        hilog.info(DOMAIN, TAG, 'MediaSession: 播放命令')
        this.playVideo()
      }
      this.mediaSessionService.onPause = () => {
        hilog.info(DOMAIN, TAG, 'MediaSession: 暂停命令')
        this.pauseVideo()
      }
      this.mediaSessionService.onStop = () => {
        hilog.info(DOMAIN, TAG, 'MediaSession: 停止命令')
        this.stopVideoAndClearState()
      }
      this.mediaSessionService.onSeek = (position: number) => {
        hilog.info(DOMAIN, TAG, `MediaSession: 跳转命令 ${position}ms`)
        this.seekVideo(position)
      }

      hilog.info(DOMAIN, TAG, '媒体会话初始化完成')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `媒体会话初始化失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 设置网络状态监听
   */
  private setupNetworkListener(): void {
    // 网络可用
    this.netCon.on('netAvailable', () => {
      hilog.info(DOMAIN, TAG, '========================================')
      hilog.info(DOMAIN, TAG, '网络可用，尝试恢复播放')
      hilog.info(DOMAIN, TAG, '========================================')

      // 更新网络状态
      this.networkStatus = 'available'

      // 如果之前正在播放，尝试恢复播放
      if (this.dlnaUri.length > 0 && this.dlnaIsPlaying) {
        this.mainVideoController.start()
        hilog.info(DOMAIN, TAG, '已恢复播放')
      }
    })

    // 网络丢失
    this.netCon.on('netLost', () => {
      hilog.warn(DOMAIN, TAG, '========================================')
      hilog.warn(DOMAIN, TAG, '网络丢失，暂停播放')
      hilog.warn(DOMAIN, TAG, '========================================')

      // 更新网络状态
      this.networkStatus = 'lost'

      // 暂停播放
      this.mainVideoController.pause()
      this.dlnaIsPlaying = false
      hilog.warn(DOMAIN, TAG, '已暂停播放')
    })

    hilog.info(DOMAIN, TAG, '网络状态监听已设置')
  }

  /**
   * 异步初始化服务
   */
  private async initializeServices(): Promise<void> {
    try {
      hilog.info(DOMAIN, TAG, '========================================')
      hilog.info(DOMAIN, TAG, '开始初始化服务')
      hilog.info(DOMAIN, TAG, '========================================')

      // 1. 首先检查是否有WiFi连接
      hilog.info(DOMAIN, TAG, '步骤1: 检查WiFi连接')
      const hasWifi = await this.networkScanService.hasWifiConnection()
      if (!hasWifi) {
        hilog.warn(DOMAIN, TAG, '✗ 未检测到WiFi连接，跳过自动扫描')
        this.localIpAddress = '未连接WiFi'
        this.autoScanProgress = '请先连接WiFi后重试'
        return
      }

      // 2. 获取本机IP地址
      hilog.info(DOMAIN, TAG, '步骤2: 获取本机IP地址')
      const phoneIp = await this.networkScanService.getLocalIpAddress()
      hilog.info(DOMAIN, TAG, `getLocalIpAddress返回: "${phoneIp}", 长度: ${phoneIp.length}`)

      if (phoneIp && phoneIp.length > 0 && phoneIp !== '获取中...') {
        this.localIpAddress = phoneIp
        hilog.info(DOMAIN, TAG, `✓ 本机IP已更新到UI状态: ${this.localIpAddress}`)
      } else {
        hilog.warn(DOMAIN, TAG, '✗ 无法获取有效本机IP，使用占位符')
        this.localIpAddress = '获取中...'
      }

      // 3. 加载保存的车机IP地址
      hilog.info(DOMAIN, TAG, '步骤3: 加载车机IP配置')
      await this.loadCarIpAddress()

      // 4. 设置网络状态监听
      hilog.info(DOMAIN, TAG, '步骤4: 设置网络监听')
      this.setupNetworkListener()

      // 4.5. 提前设置WebSocket回调（避免时序问题）
      hilog.info(DOMAIN, TAG, '步骤4.5: 提前设置WebSocket回调')
      this.setupWebSocketCallbacks()

      // 5. 先尝试使用保存的IP连接
      hilog.info(DOMAIN, TAG, '步骤5: 尝试连接到保存的车机IP')
      const connected = await this.tryConnectToCar(this.carIpAddress)

      // 6. 如果连接失败，启动自动扫描
      if (!connected) {
        hilog.info(DOMAIN, TAG, '步骤6: 保存IP连接失败，启动自动扫描')
        await this.autoScanAndConnect()
      } else {
        hilog.info(DOMAIN, TAG, '步骤6: 使用保存IP连接成功')
      }

      hilog.info(DOMAIN, TAG, '========================================')
      hilog.info(DOMAIN, TAG, '服务初始化完成')
      hilog.info(DOMAIN, TAG, `本机IP: ${this.localIpAddress}`)
      hilog.info(DOMAIN, TAG, `车机IP: ${this.carIpAddress}`)
      hilog.info(DOMAIN, TAG, `WebSocket连接: ${this.webSocketConnected}`)
      hilog.info(DOMAIN, TAG, '========================================')

    } catch (error) {
      hilog.error(DOMAIN, TAG, `启动服务失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 启动定期状态检查
   */
  private startPeriodicStatusCheck(): void {
    this.statusCheckTimer = setInterval(() => {
      this.checkAllServiceStatus()
    }, this.STATUS_CHECK_INTERVAL)

    hilog.info(DOMAIN, TAG, `定期状态检查已启动，间隔: ${this.STATUS_CHECK_INTERVAL}ms`)
  }

  /**
   * 检查所有服务状态（定期调用，实时更新UI）
   */
  private checkAllServiceStatus(): void {
    // 检查WebSocket连接状态
    const wsConnected = this.carWebSocketClient.isClientConnected()

    hilog.debug(DOMAIN, TAG, `checkAllServiceStatus: wsConnected=${wsConnected}, webSocketConnected=${this.webSocketConnected}`)

    // 强制同步状态：不管是否变化，都更新一次
    this.webSocketConnected = wsConnected
    this.carConnectionStatus = wsConnected ?
      `已连接: ${this.carIpAddress}` : '未连接'

    // 更新最后连接时间
    if (wsConnected) {
      this.lastConnectionTime = Date.now()
    }

    hilog.info(DOMAIN, TAG, `✓ 状态已同步: webSocketConnected=${this.webSocketConnected}, carIpAddress=${this.carIpAddress}`)
  }

  /**
   * 加载保存的车机IP地址
   */
  private async loadCarIpAddress() {
    try {
      if (!this.dataPreferences) {
        this.dataPreferences = await preferences.getPreferences(getContext(), 'car_ip_config')
      }
      const savedIp = await this.dataPreferences.get('car_ip_address', '192.168.200.47') as string
      this.carIpAddress = savedIp
      hilog.info(DOMAIN, TAG, `从配置加载车机IP: ${this.carIpAddress}`)
    } catch (err) {
      hilog.error(DOMAIN, TAG, '加载车机IP失败')
    }
  }

  /**
   * 保存车机IP地址
   */
  private async saveCarIpAddress(ip: string) {
    try {
      if (!this.dataPreferences) {
        this.dataPreferences = await preferences.getPreferences(getContext(), 'car_ip_config')
      }
      await this.dataPreferences.put('car_ip_address', ip)
      await this.dataPreferences.flush()
      this.carIpAddress = ip
      hilog.info(DOMAIN, TAG, `车机IP已保存: ${ip}`)
    } catch (err) {
      hilog.error(DOMAIN, TAG, '保存车机IP失败')
    }
  }

  /**
   * 设置所有WebSocket命令回调
   */
  private setupWebSocketCallbacks(): void {
    hilog.info(DOMAIN, TAG, '设置WebSocket命令回调')

    // 播放命令回调
    this.carWebSocketClient.setOnPlayCommand((uri: string, httpHeaders: Record<string, string>) => {
      hilog.info(DOMAIN, TAG, `========================================`)
      hilog.info(DOMAIN, TAG, `收到WebSocket播放命令`)
      hilog.info(DOMAIN, TAG, `URI长度: ${uri.length}`)
      hilog.info(DOMAIN, TAG, `HTTP头数量: ${Object.keys(httpHeaders).length}`)
      hilog.info(DOMAIN, TAG, `========================================`)

      // 更新状态（Video组件会自动加载并播放）
      this.dlnaUri = uri
      this.dlnaHttpHeaders = httpHeaders

      // 进入手机播放模式（启用双向同步）
      this.isPhoneMode = true
      this.videoPlayer.setPhoneMode(true)

      hilog.info(DOMAIN, TAG, `已更新dlnaUri，Video组件会自动播放，已进入手机播放模式`)
    })

    // 同步启动命令回调（play_and_seek）
    this.carWebSocketClient.setOnPlayAndSeek((uri: string, httpHeaders: Record<string, string>, position: number) => {
      hilog.info(DOMAIN, TAG, `========================================`)
      hilog.info(DOMAIN, TAG, `收到同步启动命令`)
      hilog.info(DOMAIN, TAG, `目标进度: ${position}ms (${Math.floor(position / 1000)}s)`)
      hilog.info(DOMAIN, TAG, `========================================`)

      // 更新状态
      this.dlnaUri = uri
      this.dlnaHttpHeaders = httpHeaders

      // 进入手机播放模式（启用双向同步）
      this.isPhoneMode = true
      this.videoPlayer.setPhoneMode(true)

      // 等待视频加载后跳转并暂停
      hilog.info(DOMAIN, TAG, `等待视频加载...`)

      // 延迟1秒后跳转并暂停（给视频一些加载时间）
      setTimeout(() => {
        const positionSeconds = Math.floor(position / 1000)
        hilog.info(DOMAIN, TAG, `跳转到 ${positionSeconds}s 并暂停`)
        this.mainVideoController.setCurrentTime(positionSeconds)
        this.mainVideoController.pause()

        // 再延迟1秒确保暂停完成
        setTimeout(() => {
          hilog.info(DOMAIN, TAG, `视频已跳转到指定位置并暂停，等待车机端同时播放命令`)
        }, 1000)
      }, 1000)
    })

    // 恢复播放命令回调（resume）
    this.carWebSocketClient.setOnResumeCommand(() => {
      hilog.info(DOMAIN, TAG, `收到同时播放命令，开始播放`)
      this.mainVideoController.start()
      this.dlnaIsPlaying = true
    })

    // 暂停命令回调
    this.carWebSocketClient.setOnPauseCommand(() => {
      hilog.info(DOMAIN, TAG, `收到暂停命令`)
      this.mainVideoController.pause()
    })

    // 停止命令回调
    this.carWebSocketClient.setOnStopCommand(() => {
      hilog.info(DOMAIN, TAG, `收到停止命令`)
      this.stopVideoAndClearState()
    })

    // 跳转命令回调
    this.carWebSocketClient.setOnSeekCommand((position: number) => {
      hilog.info(DOMAIN, TAG, `收到跳转命令: ${position}`)
      this.mainVideoController.setCurrentTime(position)
      this.dlnaPosition = position
    })

    // 进度更新回调（状态优先级同步）
    this.carWebSocketClient.setOnProgressUpdate((position: number, duration: number, isPlaying: boolean) => {
      hilog.info(DOMAIN, TAG, `收到进度更新: ${position}/${duration}, playing=${isPlaying}, prepared=${this.mainVideoPrepared}`)

      // 更新UI状态
      this.dlnaPosition = position
      this.dlnaDuration = duration

      // 【状态优先】先同步播放状态，再同步进度
      if (this.dlnaIsPlaying !== isPlaying) {
        hilog.info(DOMAIN, TAG, `播放状态变化: ${this.dlnaIsPlaying} -> ${isPlaying}`)

        // 同步播放状态
        if (isPlaying) {
          this.mainVideoController.start()
        } else {
          this.mainVideoController.pause()
        }

        this.dlnaIsPlaying = isPlaying
      }

      // 然后同步进度（只在播放状态下）
      if (isPlaying && this.mainVideoPrepared) {
        // 使用VideoPlayer的同步机制（已优化：动态阈值、防抖、平滑seek）
        this.videoPlayer.syncProgress(position, duration, isPlaying)
      } else {
        hilog.debug(DOMAIN, TAG, '暂停状态下不执行进度同步')
      }
    })

    // 连接断开回调：立即暂停视频并清理状态
    this.carWebSocketClient.setOnDisconnected(() => {
      hilog.warn(DOMAIN, TAG, `========================================`)
      hilog.warn(DOMAIN, TAG, `与车机断开连接，立即暂停视频并清理状态`)
      hilog.warn(DOMAIN, TAG, `========================================`)

      // 更新连接状态
      this.webSocketConnected = false
      this.carConnectionStatus = '未连接'
      this.isConnecting = false

      // 立即暂停视频
      this.mainVideoController.pause()
      this.dlnaIsPlaying = false
      this.dlnaPosition = 0
      this.dlnaDuration = 0

      // 清理URI和状态，隐藏视频卡片
      this.dlnaUri = ''
      this.dlnaHttpHeaders = {}
      this.isPhoneMode = false

      hilog.info(DOMAIN, TAG, '视频已暂停，状态已清理，等待重新连接')
    })

    hilog.info(DOMAIN, TAG, 'WebSocket命令回调设置完成')
  }

  /**
   * 尝试连接到车机端（返回是否成功）
   */
  private async tryConnectToCar(ip: string): Promise<boolean> {
    hilog.info(DOMAIN, TAG, `========================================`)
    hilog.info(DOMAIN, TAG, `尝试连接到车机端: ${ip}`)
    hilog.info(DOMAIN, TAG, `========================================`)

    this.isConnecting = true

    return new Promise<boolean>((resolve) => {
      let connectionTimeoutId: number = -1

      // ⚠️ 重要：在连接之前设置所有回调
      hilog.info(DOMAIN, TAG, '步骤1: 设置WebSocket回调')

      // 连接成功回调
      this.carWebSocketClient.setOnConnected(() => {
        hilog.info(DOMAIN, TAG, '========================================')
        hilog.info(DOMAIN, TAG, '✓ WebSocket连接成功')
        hilog.info(DOMAIN, TAG, `车机IP: ${ip}`)
        hilog.info(DOMAIN, TAG, '========================================')

        // 清除超时定时器
        if (connectionTimeoutId !== -1) {
          clearTimeout(connectionTimeoutId)
          connectionTimeoutId = -1
        }

        // 更新连接状态（@State会触发UI刷新）
        this.webSocketConnected = true
        this.carConnectionStatus = `已连接: ${ip}`
        this.carIpAddress = ip
        this.lastConnectionTime = Date.now()
        this.isConnecting = false

        hilog.info(DOMAIN, TAG, `状态已更新: webSocketConnected=${this.webSocketConnected}, carIpAddress=${this.carIpAddress}`)

        resolve(true)
      })

      // 注意：WebSocket回调已在应用启动时预先设置（setupWebSocketCallbacks）

      // ⚠️ 重要：所有回调设置完成后，才开始连接
      // 注意：connect方法立即返回，连接是异步的
      hilog.info(DOMAIN, TAG, '步骤2: 开始连接到车机端')
      this.carWebSocketClient.connect(ip)

      // 3秒超时检查
      connectionTimeoutId = setTimeout(() => {
        if (!this.webSocketConnected) {
          hilog.warn(DOMAIN, TAG, `========================================`)
          hilog.warn(DOMAIN, TAG, `✗ 连接超时: ${ip}`)
          hilog.warn(DOMAIN, TAG, `========================================`)
          this.isConnecting = false
          resolve(false)
        }
      }, 3000)
    })
  }

  /**
   * 自动扫描并连接车机端
   */
  private async autoScanAndConnect(): Promise<void> {
    if (this.hasAttemptedAutoScan) {
      hilog.info(DOMAIN, TAG, '已经尝试过自动扫描，跳过')
      return
    }

    // 首先检查WiFi连接
    const hasWifi = await this.networkScanService.hasWifiConnection()
    if (!hasWifi) {
      hilog.warn(DOMAIN, TAG, '未检测到WiFi连接，跳过自动扫描')
      this.autoScanProgress = '请先连接WiFi后重试'
      this.scanProgressDetail = '请先连接WiFi后重试'
      return
    }

    this.hasAttemptedAutoScan = true
    this.isAutoScanning = true
    this.autoScanProgress = '正在获取本机IP...'
    this.scanProgressDetail = '获取本机IP...'

    hilog.info(DOMAIN, TAG, '开始自动扫描车机端')

    try {
      // 获取本机IP
      const phoneIp = await this.networkScanService.getLocalIpAddress()
      if (!phoneIp || phoneIp.length === 0) {
        hilog.error(DOMAIN, TAG, '无法获取本机IP，跳过自动扫描')
        this.autoScanProgress = '无法获取本机IP'
        this.scanProgressDetail = '无法获取本机IP'
        this.isAutoScanning = false
        return
      }

      hilog.info(DOMAIN, TAG, `本机IP: ${phoneIp}`)
      this.autoScanProgress = '正在扫描网段...'
      this.scanProgressDetail = '扫描网段中...'

      // 开始扫描
      this.networkScanService.scanNetwork(phoneIp, {
        onProgress: (current: number, total: number, currentIp: string) => {
          const progress = `扫描中: ${current}/${total}`
          this.autoScanProgress = progress
          this.scanProgressDetail = progress
          hilog.debug(DOMAIN, TAG, `扫描进度: ${current}/${total}`)
        },
        onFound: (result) => {
          hilog.info(DOMAIN, TAG, `发现车机端: ${result.ip}`)
          this.scanProgressDetail = `发现车机: ${result.ip}`
        },
        onComplete: async (results) => {
          this.isAutoScanning = false

          if (results.length > 0) {
            hilog.info(DOMAIN, TAG, `扫描完成，找到 ${results.length} 个车机端`)

            // 尝试连接第一个找到的车机端
            const firstCar = results[0]
            this.autoScanProgress = `正在连接 ${firstCar.ip}...`
            this.scanProgressDetail = `连接中: ${firstCar.ip}`

            const connected = await this.tryConnectToCar(firstCar.ip)

            if (connected) {
              hilog.info(DOMAIN, TAG, `自动连接成功: ${firstCar.ip}`)
              this.autoScanProgress = `已自动连接: ${firstCar.ip}`
              this.scanProgressDetail = ''

              // 保存IP地址
              await this.saveCarIpAddress(firstCar.ip)

              // 3秒后清除进度信息
              setTimeout(() => {
                this.autoScanProgress = ''
              }, 3000)
            } else {
              hilog.warn(DOMAIN, TAG, `自动连接失败: ${firstCar.ip}`)
              this.autoScanProgress = '连接失败，请手动设置'
              this.scanProgressDetail = '连接失败'
            }
          } else {
            hilog.warn(DOMAIN, TAG, '扫描完成，未找到车机端')
            this.autoScanProgress = '未找到车机端，请手动设置'
            this.scanProgressDetail = '未找到车机'
          }
        },
        onError: (error) => {
          this.isAutoScanning = false
          hilog.error(DOMAIN, TAG, `扫描失败: ${error}`)
          this.autoScanProgress = `扫描失败: ${error}`
          this.scanProgressDetail = `扫描失败: ${error}`
        }
      })
    } catch (error) {
      this.isAutoScanning = false
      hilog.error(DOMAIN, TAG, `自动扫描异常: ${JSON.stringify(error)}`)
      this.autoScanProgress = '扫描异常'
      this.scanProgressDetail = '扫描异常'
    }
  }

  /**
   * 连接到车机端
   */
  private connectToCar(): void {
    hilog.info(DOMAIN, TAG, `尝试连接到车机端: ${this.carIpAddress}`)
    this.isConnecting = true

    // 设置WebSocket回调
    this.setupWebSocketCallbacks()

    // 连接到车机端
    this.carWebSocketClient.connect(this.carIpAddress)

    // 延迟检查连接状态
    setTimeout(() => {
      this.isConnecting = false
    }, 3000)
  }

  /**
   * 重新连接到车机端
   */
  private reconnectToCar(): void {
    hilog.info(DOMAIN, TAG, '用户手动触发重新连接')

    // 设置刷新状态
    this.isRefreshing = true
    this.scanProgressDetail = '正在连接...'

    // 先断开现有连接
    if (this.carWebSocketClient.isClientConnected()) {
      hilog.info(DOMAIN, TAG, '断开现有连接')
      this.carWebSocketClient.disconnect()
    }

    // 等待一小段时间后重新连接
    setTimeout(() => {
      hilog.info(DOMAIN, TAG, `重新连接到车机端: ${this.carIpAddress}`)
      this.connectToCar()

      // 3秒后重置刷新状态（无论成功或失败）
      setTimeout(() => {
        this.isRefreshing = false
        this.scanProgressDetail = ''
      }, 3000)
    }, 500)
  }

  /**
   * 显示IP设置对话框
   */
  private showIpDialog(): void {
    hilog.info(DOMAIN, TAG, '显示IP设置对话框')

    // 如果已有对话框实例，先关闭
    if (this.ipDialogController !== undefined) {
      this.ipDialogController.close()
      this.ipDialogController = undefined
    }

    // 初始化临时变量
    this.tempIp = this.carIpAddress

    // 创建新的对话框实例
    this.ipDialogController = new CustomDialogController({
      builder: CarIpDialog({
        ipAddress: this.tempIp,
        onSave: (ip: string) => {
          this.saveCarIpAddressAndConnect(ip)
        }
      }),
      customStyle: true,
      alignment: DialogAlignment.Center
    })

    this.ipDialogController.open()
  }

  /**
   * 保存IP地址并连接
   */
  private async saveCarIpAddressAndConnect(ip: string): Promise<void> {
    await this.saveCarIpAddress(ip)
    hilog.info(DOMAIN, TAG, `IP已保存，准备连接: ${ip}`)

    // 断开现有连接
    if (this.carWebSocketClient.isClientConnected()) {
      this.carWebSocketClient.disconnect()
    }

    // 等待1秒后连接
    setTimeout(() => {
      this.connectToCar()
    }, 1000)
  }

  /**
   * 设置DLNA服务回调（已移除DLNA服务，此方法不再需要）
   */

  aboutToDisappear(): void {
    hilog.info(DOMAIN, TAG, 'Page aboutToDisappear')

    // 🔧 修复内存泄漏：清理状态检查定时器并重置
    if (this.statusCheckTimer !== -1) {
      clearInterval(this.statusCheckTimer)
      this.statusCheckTimer = -1
    }

    this.cleanup()
  }

  /**
   * 播放视频（支持双向同步）
   */
  private async playVideo(): Promise<void> {
    hilog.info(DOMAIN, TAG, `playVideo被调用，dlnaUri长度: ${this.dlnaUri.length}, isPhoneMode=${this.isPhoneMode}`)

    if (this.dlnaUri.length === 0) {
      hilog.warn(DOMAIN, TAG, '无播放URI，无法播放')
      return
    }

    try {
      hilog.info(DOMAIN, TAG, `开始播放视频，URI: ${this.dlnaUri.substring(0, 100)}...`)
      hilog.info(DOMAIN, TAG, `HTTP头数量: ${Object.keys(this.dlnaHttpHeaders).length}`)
      hilog.info(DOMAIN, TAG, `HTTP头: ${JSON.stringify(this.dlnaHttpHeaders)}`)

      // 启动后台服务（保持后台播放）
      await this.backgroundService.start()
      await this.mediaSessionService.startBackgroundService()

      // 设置媒体元数据
      await this.updateMediaMetadata()

      // 确保使用视频+音频模式（完整播放）
      // 传递HTTP头以支持B站反爬虫验证
      await this.videoPlayer.play(this.dlnaUri, this.dlnaHttpHeaders)

      this.dlnaIsPlaying = true

      // 双向同步：如果在手机播放模式，发送播放命令到车机端
      if (this.isPhoneMode && this.webSocketConnected) {
        hilog.info(DOMAIN, TAG, '手机模式：发送播放命令到车机端')
        this.carWebSocketClient.sendControlCommand('play')
      }

      hilog.info(DOMAIN, TAG, '视频播放成功')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `播放失败: ${JSON.stringify(error)}`)
      this.dlnaIsPlaying = false
    }
  }

  /**
   * 更新媒体元数据
   */
  private async updateMediaMetadata(): Promise<void> {
    try {
      // 从URL提取标题（简化处理，实际可以从元数据获取）
      let title = 'FSCast 投屏视频'
      if (this.dlnaUri.length > 0) {
        try {
          // ArkTS不支持URL类，使用字符串解析
          const urlStr = this.dlnaUri
          const queryIndex = urlStr.indexOf('?')
          const pathOnly = queryIndex >= 0 ? urlStr.substring(0, queryIndex) : urlStr
          const lastSlashIndex = pathOnly.lastIndexOf('/')
          if (lastSlashIndex >= 0 && lastSlashIndex < pathOnly.length - 1) {
            const fileName = pathOnly.substring(lastSlashIndex + 1)
            if (fileName && fileName.length > 0) {
              title = decodeURIComponent(fileName)
            }
          }
        } catch (e) {
          // URL解析失败，使用默认标题
        }
      }

      // 设置元数据
      await this.mediaSessionService.setMetadata(
        title,
        'FSCast Remote',
        this.dlnaDuration
      )

      // 设置播放状态
      await this.mediaSessionService.setPlaybackState(
        this.dlnaIsPlaying,
        this.dlnaPosition,
        this.dlnaDuration
      )

      hilog.info(DOMAIN, TAG, `媒体元数据已更新: ${title}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `更新媒体元数据失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 暂停视频（支持双向同步）
   */
  private async pauseVideo(): Promise<void> {
    try {
      await this.videoPlayer.pause()
      this.dlnaIsPlaying = false

      // 双向同步：如果在手机播放模式，发送暂停命令到车机端
      if (this.isPhoneMode && this.webSocketConnected) {
        hilog.info(DOMAIN, TAG, '手机模式：发送暂停命令到车机端')
        this.carWebSocketClient.sendControlCommand('pause')
      }
    } catch (error) {
      hilog.error(DOMAIN, TAG, `暂停失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 停止视频
   */
  private async stopVideo(): Promise<void> {
    try {
      await this.videoPlayer.stop()
      this.dlnaIsPlaying = false
      this.dlnaPosition = 0
    } catch (error) {
      hilog.error(DOMAIN, TAG, `停止失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 停止视频并清空状态（用于stop命令和播放完成，支持双向同步）
   */
  private async stopVideoAndClearState(): Promise<void> {
    hilog.info(DOMAIN, TAG, '========================================')
    hilog.info(DOMAIN, TAG, 'stopVideoAndClearState: 停止播放并清空状态')
    hilog.info(DOMAIN, TAG, '========================================')

    try {
      // 双向同步：如果在手机播放模式，发送停止命令到车机端
      if (this.isPhoneMode && this.webSocketConnected) {
        hilog.info(DOMAIN, TAG, '手机模式：发送停止命令到车机端')
        this.carWebSocketClient.sendControlCommand('stop')
      }

      // 停止视频播放
      await this.videoPlayer.stop()

      // 清空URI和HTTP头
      this.dlnaUri = ''
      this.dlnaHttpHeaders = {}

      // 重置播放状态
      this.dlnaIsPlaying = false
      this.dlnaPosition = 0
      this.dlnaDuration = 0
      this.mainVideoPrepared = false
      this.phoneActualPosition = 0

      // 退出手机播放模式
      this.isPhoneMode = false
      this.videoPlayer.setPhoneMode(false)

      // 停止后台服务和媒体会话
      await this.backgroundService.stop()
      await this.mediaSessionService.setPlaybackState(false, 0, 0)

      hilog.info(DOMAIN, TAG, '状态已清空，回到等待投屏状态')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `清空状态失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 跳转（支持双向同步）
   * 添加操作锁机制，防止两端同时操作产生冲突
   */
  private async seekVideo(position: number): Promise<void> {
    // 检查操作锁（2秒内不允许重复操作）
    const now = Date.now()
    if (now - this.lastSeekTime < 2000) {
      hilog.warn(DOMAIN, TAG, 'Seek操作被锁阻止（距离上次操作不足2秒）')
      return
    }

    // 更新锁时间
    this.lastSeekTime = now

    try {
      await this.videoPlayer.seek(position)
      this.dlnaPosition = position

      // 双向同步：如果在手机播放模式，发送跳转命令到车机端
      if (this.isPhoneMode && this.webSocketConnected) {
        hilog.info(DOMAIN, TAG, `手机模式：发送跳转命令到车机端，位置: ${position}ms`)
        this.carWebSocketClient.sendControlCommand('seek', { 'position': position })
      }
    } catch (error) {
      hilog.error(DOMAIN, TAG, `跳转失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 显示复制成功提示
   */
  private showCopySuccessToast(): void {
    hilog.info(DOMAIN, TAG, 'URL已复制到剪贴板')
    // TODO: 可以添加Toast提示
  }

  /**
   * 下载视频（带进度显示）
   */
  private async downloadVideo(uri: string, headers: Record<string, string>): Promise<void> {
    if (uri.length === 0) {
      hilog.warn(DOMAIN, TAG, '无URI可下载')
      return
    }

    let httpRequest: http.HttpRequest | null = null

    try {
      hilog.info(DOMAIN, TAG, `开始下载视频: ${uri.substring(0, 100)}...`)

      // 生成文件名
      const fileName = this.extractFileName(uri)

      // 获取系统Download目录
      const downloadDir = Environment.getUserDownloadDir()
      const downloadPath = `${downloadDir}/${fileName}`

      hilog.info(DOMAIN, TAG, `下载目录: ${downloadDir}`)

      // 创建HTTP请求
      httpRequest = http.createHttp()

      // 设置请求选项，添加进度回调
      const options: http.HttpRequestOptions = {
        method: http.RequestMethod.GET,
        header: headers as Record<string, string>,
        connectTimeout: 30000,
        readTimeout: 60000,
        progress: (currentSize: number, totalSize: number) => {
          // 计算下载进度百分比
          if (totalSize > 0) {
            const progress = Math.floor((currentSize / totalSize) * 100)
            hilog.debug(DOMAIN, TAG, `下载进度: ${progress}% (${currentSize}/${totalSize})`)

            // 直接更新下载进度状态
            this.downloadProgress = progress
          }
        }
      } as http.HttpRequestOptions

      hilog.info(DOMAIN, TAG, `开始下载到: ${downloadPath}`)

      // 发送请求（后台下载，不阻塞UI）
      const response = await httpRequest.request(uri, options) as http.HttpResponse

      if (response.responseCode >= 200 && response.responseCode < 300) {
        // 将响应写入文件
        const file = await fileIo.open(downloadPath, fileIo.OpenMode.CREATE | fileIo.OpenMode.WRITE_ONLY)

        if (response.result instanceof ArrayBuffer) {
          await fileIo.write(file.fd, response.result)
        }

        await fileIo.close(file.fd)

        hilog.info(DOMAIN, TAG, `视频下载成功: ${downloadPath}`)

        // 设置进度为100%并结束下载状态
        this.downloadProgress = 100
        this.isDownloading = false
      } else {
        hilog.error(DOMAIN, TAG, `下载失败，状态码: ${response.responseCode}`)
        this.isDownloading = false
        this.downloadProgress = 0
      }
    } catch (error) {
      hilog.error(DOMAIN, TAG, `下载视频失败: ${JSON.stringify(error)}`)
      this.isDownloading = false
      this.downloadProgress = 0
    } finally {
      // 销毁HTTP请求对象
      if (httpRequest !== null) {
        httpRequest.destroy()
      }
    }
  }

  /**
   * 从URL提取文件名
   */
  private extractFileName(url: string): string {
    try {
      // ArkTS不支持URL类，使用字符串解析
      const queryIndex = url.indexOf('?')
      const pathOnly = queryIndex >= 0 ? url.substring(0, queryIndex) : url
      const lastSlashIndex = pathOnly.lastIndexOf('/')

      if (lastSlashIndex >= 0 && lastSlashIndex < pathOnly.length - 1) {
        let fileName = pathOnly.substring(lastSlashIndex + 1)

        // URL解码
        try {
          fileName = decodeURIComponent(fileName)
        } catch (e) {
          // 解码失败，使用原始文件名
        }

        // 如果没有扩展名，添加.mp4
        if (!fileName.includes('.')) {
          fileName = `${fileName}.mp4`
        }

        return fileName
      }

      // 默认文件名
      return `video_${Date.now()}.mp4`
    } catch (error) {
      hilog.error(DOMAIN, TAG, `提取文件名失败: ${JSON.stringify(error)}`)
      return `video_${Date.now()}.mp4`
    }
  }

  /**
   * 清理资源
   */
  private cleanup(): void {
    // 断开车机端连接
    this.carWebSocketClient.disconnect()

    // 释放视频播放器
    this.videoPlayer.release()

    // 释放媒体服务和后台服务
    this.mediaSessionService.release()
    this.backgroundService.stop()
  }

  // ==================== UI 构建 ====================

  build() {
    Scroll() {
      Column({ space: 12 }) {
        // 连接状态卡片（独立显示）
        ConnectionStatusCard({
          webSocketConnected: this.webSocketConnected,
          carIpAddress: this.carIpAddress,
          localIpAddress: this.localIpAddress,
          isRefreshing: this.isRefreshing,
          scanProgress: this.scanProgressDetail,
          onRefresh: async () => {
            // 首先检查WiFi连接
            const hasWifi = await this.networkScanService.hasWifiConnection()
            if (!hasWifi) {
              this.autoScanProgress = '请先连接WiFi后重试'
              this.scanProgressDetail = '请先连接WiFi后重试'
              setTimeout(() => {
                this.autoScanProgress = ''
                this.scanProgressDetail = ''
              }, 3000)
              return
            }

            // 重置扫描标记，允许重新扫描
            this.hasAttemptedAutoScan = false
            // 启动自动扫描并重新连接
            await this.autoScanAndConnect()
          }
        })

        // 视频URL卡片（收到URI且WebSocket连接时显示）
        if (this.dlnaUri.length > 0 && this.webSocketConnected) {
          VideoUrlCard({
            dlnaUri: this.dlnaUri,
            dlnaHttpHeaders: this.dlnaHttpHeaders,
            isDownloading: $isDownloading,
            downloadProgress: $downloadProgress,
            onCopy: () => {
              this.showCopySuccessToast()
            },
            onDownload: (uri: string, headers: Record<string, string>) => {
              this.downloadVideo(uri, headers)
            }
          })
        }

        // 视频播放器（有URI且WebSocket连接时显示）
        if (this.dlnaUri.length > 0 && this.webSocketConnected) {
          VideoPlayerCard({
            dlnaUri: this.dlnaUri,
            dlnaHttpHeaders: this.dlnaHttpHeaders,
            dlnaIsPlaying: this.dlnaIsPlaying,
            dlnaPosition: this.dlnaPosition,
            dlnaDuration: this.dlnaDuration,
            videoController: this.mainVideoController,
            onPlayStateChange: (isPlaying: boolean) => {
              this.dlnaIsPlaying = isPlaying
            },
            onProgressUpdate: (position: number, duration: number) => {
              this.phoneActualPosition = position
              this.dlnaDuration = duration
            },
            onCompletion: () => {
              this.stopVideoAndClearState()
            },
            onError: (error: ESObject) => {
              hilog.error(DOMAIN, TAG, `视频播放错误: ${JSON.stringify(error)}`)
            }
          })
        }

        Blank().height(16)
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 20 })
      .backgroundColor('#0F172A')
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#0F172A')
    .scrollBar(BarState.Auto)
    .scrollBarColor('#334155')
  }
}
