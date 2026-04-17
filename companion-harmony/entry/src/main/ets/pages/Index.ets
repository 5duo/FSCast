/**
 * FSCast Remote 主页面
 * DLNA DMR + 设备发现响应 + 同步客户端
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { preferences } from '@kit.ArkData'
import { http } from '@kit.NetworkKit'
import media from '@ohos.multimedia.media'
import { SsdpServer } from '../service/SsdpServer'
import { DlnaHttpServer } from '../service/DlnaHttpServer'
import { VideoPlayer } from '../service/VideoPlayer'
import { SyncClient } from '../service/SyncClient'
import { PhoneDiscoveryResponse } from '../service/PhoneDiscoveryResponse'
import { CarWebSocketClient } from '../service/CarWebSocketClient'

const TAG = 'IndexPage'
const DOMAIN = 0x0000

@Entry
@Component
struct Index {
  // ===== 服务实例 =====
  private ssdpServer: SsdpServer = new SsdpServer()
  private dlnaHttpServer: DlnaHttpServer = new DlnaHttpServer()
  private videoPlayer: VideoPlayer = new VideoPlayer()
  private syncClient: SyncClient = new SyncClient()
  private phoneDiscoveryResponse: PhoneDiscoveryResponse = new PhoneDiscoveryResponse()
  private carWebSocketClient: CarWebSocketClient = new CarWebSocketClient()

  // ===== 车机端IP地址 =====
  private carIpAddress: string = '192.168.200.47'  // 默认车机IP
  private dataPreferences?: preferences.Preferences  // 用于持久化配置

  // 加载保存的车机IP地址
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

  // 保存车机IP地址
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

  // ===== 同步状态 =====
  @State syncConnected: boolean = false

  // ===== WebSocket连接状态 =====
  @State webSocketConnected: boolean = false
  @State carConnectionStatus: string = '未连接'

  // ===== 播放模式 =====
  @State playbackMode: 'audio_only' | 'video_audio' = 'audio_only'

  // ===== 服务运行状态 =====
  @State ssdpRunning: boolean = false
  @State dlnaHttpRunning: boolean = false
  @State discoveryResponseRunning: boolean = false

  // ===== HTTP测试结果 =====
  @State httpTestResult: string = ''
  @State httpTestLoading: boolean = false

  // ===== 独立视频播放器 =====
  @State testVideoPlaying: boolean = false
  @State testVideoProgress: number = 0
  @State testVideoDuration: number = 0
  private testVideoController: VideoController = new VideoController()

  aboutToAppear(): void {
    hilog.info(DOMAIN, TAG, 'Page aboutToAppear')

    // 设置DLNA服务回调
    this.setupDlnaCallbacks()

    // 异步初始化服务
    this.initializeServices()
  }

  /**
   * 异步初始化服务
   */
  private async initializeServices(): Promise<void> {
    try {
      // 加载保存的车机IP地址
      await this.loadCarIpAddress()

      // 启动SSDP服务
      await this.ssdpServer.start()
      this.ssdpRunning = true
      hilog.info(DOMAIN, TAG, 'SSDP服务已启动')

      // 启动DLNA HTTP服务器
      await this.dlnaHttpServer.start()
      this.dlnaHttpRunning = true
      hilog.info(DOMAIN, TAG, 'DLNA HTTP服务器已启动')

      // 启动设备发现响应服务
      await this.phoneDiscoveryResponse.start()
      this.discoveryResponseRunning = true
      hilog.info(DOMAIN, TAG, '设备发现响应服务已启动')

      // 连接到车机端WebSocket
      this.connectToCar()

    } catch (error) {
      hilog.error(DOMAIN, TAG, `启动服务失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 连接到车机端
   */
  private connectToCar(): void {
    hilog.info(DOMAIN, TAG, `尝试连接到车机端: ${this.carIpAddress}`)

    // 设置WebSocket回调
    this.carWebSocketClient.setOnPlayCommand((uri: string, httpHeaders: Record<string, string>) => {
      hilog.info(DOMAIN, TAG, `========================================`)
      hilog.info(DOMAIN, TAG, `收到WebSocket播放命令`)
      hilog.info(DOMAIN, TAG, `URI长度: ${uri.length}`)
      hilog.info(DOMAIN, TAG, `URI: ${uri.substring(0, 100)}...`)
      hilog.info(DOMAIN, TAG, `HTTP头数量: ${Object.keys(httpHeaders).length}`)
      hilog.info(DOMAIN, TAG, `========================================`)

      // 更新状态（Video组件会自动加载并播放）
      this.dlnaUri = uri
      this.dlnaHttpHeaders = httpHeaders

      hilog.info(DOMAIN, TAG, `已更新dlnaUri，Video组件会自动播放`)
    })

    // 新的同步启动流程：播放并跳转
    this.carWebSocketClient.setOnPlayAndSeek((uri: string, httpHeaders: Record<string, string>, position: number) => {
      hilog.info(DOMAIN, TAG, `========================================`)
      hilog.info(DOMAIN, TAG, `收到同步启动命令`)
      hilog.info(DOMAIN, TAG, `URI长度: ${uri.length}`)
      hilog.info(DOMAIN, TAG, `目标进度: ${position}ms (${Math.floor(position / 1000)}s)`)
      hilog.info(DOMAIN, TAG, `========================================`)

      // 更新状态
      this.dlnaUri = uri
      this.dlnaHttpHeaders = httpHeaders

      // 等待视频加载后跳转并暂停
      // Video组件会自动开始加载视频
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

    // 恢复播放命令（同步启动流程的最后一步）
    this.carWebSocketClient.setOnResumeCommand(() => {
      hilog.info(DOMAIN, TAG, `收到同时播放命令，开始播放`)
      this.mainVideoController.start()
      this.dlnaIsPlaying = true
    })

    // 连接断开回调：立即暂停视频
    this.carWebSocketClient.setOnDisconnected(() => {
      hilog.warn(DOMAIN, TAG, `========================================`)
      hilog.warn(DOMAIN, TAG, `与车机断开连接，立即暂停视频`)
      hilog.warn(DOMAIN, TAG, `========================================`)

      // 立即暂停视频
      this.mainVideoController.pause()
      this.dlnaIsPlaying = false
      this.dlnaPosition = 0
      this.dlnaDuration = 0

      hilog.info(DOMAIN, TAG, '视频已暂停，等待重新连接')
    })

    this.carWebSocketClient.setOnPauseCommand(() => {
      hilog.info(DOMAIN, TAG, '收到暂停命令')
      this.mainVideoController.pause()
    })

    this.carWebSocketClient.setOnStopCommand(() => {
      hilog.info(DOMAIN, TAG, '收到停止命令')
      this.mainVideoController.stop()
      this.dlnaPosition = 0
    })

    this.carWebSocketClient.setOnSeekCommand((position: number) => {
      hilog.info(DOMAIN, TAG, `收到跳转命令: ${position}`)
      this.mainVideoController.setCurrentTime(position)
      this.dlnaPosition = position
    })

    this.carWebSocketClient.setOnProgressUpdate((position: number, duration: number, isPlaying: boolean) => {
      hilog.info(DOMAIN, TAG, `收到进度更新: ${position}/${duration}, playing=${isPlaying}, prepared=${this.mainVideoPrepared}`)

      // 更新UI状态
      this.dlnaPosition = position
      this.dlnaDuration = duration
      this.dlnaIsPlaying = isPlaying

      // 新的同步策略：每10秒检查一次进度差异
      // 如果差异超过2秒，执行重新对齐流程
      if (this.mainVideoPrepared) {
        // 计算进度差异（手机实际进度 vs 车机端进度）
        const positionDiff = Math.abs(this.phoneActualPosition - position)

        hilog.info(DOMAIN, TAG, `进度检查: 手机${this.phoneActualPosition}ms, 车机${position}ms, 差异${positionDiff}ms`)

        // 差异超过2秒时执行重新对齐
        if (positionDiff > 2000) {
          hilog.info(DOMAIN, TAG, `========================================`)
          hilog.info(DOMAIN, TAG, `进度差异过大(${positionDiff}ms)，执行重新对齐`)
          hilog.info(DOMAIN, TAG, `步骤1: 手机端暂停`)
          this.mainVideoController.pause()
          this.dlnaIsPlaying = false

          // 步骤2: 跳转到车机端进度
          const positionSeconds = Math.floor(position / 1000)
          hilog.info(DOMAIN, TAG, `步骤2: 跳转到 ${positionSeconds}s`)
          this.mainVideoController.setCurrentTime(positionSeconds)

          // 步骤3: 等待加载完成（2秒）
          hilog.info(DOMAIN, TAG, `步骤3: 等待视频加载...`)
          setTimeout(() => {
            hilog.info(DOMAIN, TAG, `步骤4: 加载完成，恢复播放`)
            this.mainVideoController.start()
            this.dlnaIsPlaying = true
            hilog.info(DOMAIN, TAG, `========================================`)
          }, 2000)
        }
      }
    })

    // 连接到车机端
    this.carWebSocketClient.connect(this.carIpAddress)

    // 检查连接状态
    this.checkConnectionStatus()
  }

  /**
   * 重新连接到车机端
   */
  private reconnectToCar(): void {
    hilog.info(DOMAIN, TAG, '用户手动刷新连接')

    // 先断开现有连接
    if (this.carWebSocketClient.isClientConnected()) {
      hilog.info(DOMAIN, TAG, '断开现有连接')
      this.carWebSocketClient.disconnect()
    }

    // 等待一小段时间后重新连接
    setTimeout(() => {
      hilog.info(DOMAIN, TAG, `重新连接到车机端: ${this.carIpAddress}`)
      this.carWebSocketClient.connect(this.carIpAddress)

      // 检查连接状态
      setTimeout(() => {
        const isConnected = this.carWebSocketClient.isClientConnected()
        this.webSocketConnected = isConnected
        this.carConnectionStatus = isConnected ? `已连接: ${this.carIpAddress}` : '未连接'
        hilog.info(DOMAIN, TAG, `重连结果: ${this.carConnectionStatus}`)
      }, 2000)
    }, 500)
  }

  /**
   * 检查连接状态
   */
  private checkConnectionStatus(): void {
    // 延迟检查，等待连接建立
    setTimeout(() => {
      const isConnected = this.carWebSocketClient.isClientConnected()
      this.webSocketConnected = isConnected
      this.carConnectionStatus = isConnected ? `已连接: ${this.carIpAddress}` : '未连接'
      hilog.info(DOMAIN, TAG, `连接状态: ${this.carConnectionStatus}`)
    }, 1000)

    // 定期检查连接状态
    setInterval(() => {
      const isConnected = this.carWebSocketClient.isClientConnected()
      if (this.webSocketConnected !== isConnected) {
        this.webSocketConnected = isConnected
        this.carConnectionStatus = isConnected ? `已连接: ${this.carIpAddress}` : '未连接'
        hilog.info(DOMAIN, TAG, `连接状态变化: ${this.carConnectionStatus}`)
      }
    }, 5000)
  }

  /**
   * 设置DLNA服务回调
   */
  private setupDlnaCallbacks(): void {
    // 播放回调
    this.dlnaHttpServer.setPlayCallback(() => {
      hilog.info(DOMAIN, TAG, 'DLNA Play命令')
      this.playVideo()
    })

    this.dlnaHttpServer.setPauseCallback(() => {
      hilog.info(DOMAIN, TAG, 'DLNA Pause命令')
      this.pauseVideo()
    })

    this.dlnaHttpServer.setStopCallback(() => {
      hilog.info(DOMAIN, TAG, 'DLNA Stop命令')
      this.stopVideo()
    })

    this.dlnaHttpServer.setSeekCallback((position: number) => {
      hilog.info(DOMAIN, TAG, `DLNA Seek命令: ${position}`)
      this.seekVideo(position)
    })

    // 同步客户端回调
    this.syncClient.setOnPlayCommand(() => {
      hilog.info(DOMAIN, TAG, '车机请求播放')
      this.playVideo()
    })

    this.syncClient.setOnPauseCommand(() => {
      hilog.info(DOMAIN, TAG, '车机请求暂停')
      this.pauseVideo()
    })

    this.syncClient.setOnStopCommand(() => {
      hilog.info(DOMAIN, TAG, '车机请求停止')
      this.stopVideo()
    })

    this.syncClient.setOnSeekCommand((position: number) => {
      hilog.info(DOMAIN, TAG, `车机请求跳转: ${position}`)
      this.seekVideo(position)
    })

    this.syncClient.setOnProgressUpdate((position: number, duration: number, isPlaying: boolean) => {
      this.dlnaIsPlaying = isPlaying
      this.dlnaPosition = position
      this.dlnaDuration = duration
    })
  }

  /**
   * 监听DLNA URI变化（已禁用，改为直接调用playVideo）
   */
  onDlnaUriChanged(): void {
    // 不再使用@Watch自动播放，改为收到play命令后直接调用
    hilog.debug(DOMAIN, TAG, `onDlnaUriChanged被触发（已禁用自动播放）`)
  }

  aboutToDisappear(): void {
    hilog.info(DOMAIN, TAG, 'Page aboutToDisappear')
    this.cleanup()
  }

  /**
   * 播放视频
   */
  private async playVideo(): Promise<void> {
    hilog.info(DOMAIN, TAG, `playVideo被调用，dlnaUri长度: ${this.dlnaUri.length}`)

    if (this.dlnaUri.length === 0) {
      hilog.warn(DOMAIN, TAG, '无播放URI，无法播放')
      return
    }

    try {
      hilog.info(DOMAIN, TAG, `开始播放视频，URI: ${this.dlnaUri.substring(0, 100)}...`)
      hilog.info(DOMAIN, TAG, `HTTP头数量: ${Object.keys(this.dlnaHttpHeaders).length}`)
      hilog.info(DOMAIN, TAG, `HTTP头: ${JSON.stringify(this.dlnaHttpHeaders)}`)

      // 确保使用视频+音频模式（完整播放）
      // 传递HTTP头以支持B站反爬虫验证
      await this.videoPlayer.play(this.dlnaUri, this.dlnaHttpHeaders)

      this.dlnaIsPlaying = true
      hilog.info(DOMAIN, TAG, '视频播放成功')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `播放失败: ${JSON.stringify(error)}`)
      this.dlnaIsPlaying = false
    }
  }

  /**
   * 测试HTTP请求 - 验证URL和HTTP头是否有效
   * 如果成功（状态码200），自动播放视频
   */
  private async testHttpUrl(): Promise<void> {
    if (this.dlnaUri.length === 0) {
      this.httpTestResult = '❌ 无URL可测试'
      return
    }

    this.httpTestLoading = true
    this.httpTestResult = '⏳ 正在请求...'

    try {
      hilog.info(DOMAIN, TAG, `开始测试HTTP请求，URL长度: ${this.dlnaUri.length}`)
      hilog.info(DOMAIN, TAG, `HTTP头数量: ${Object.keys(this.dlnaHttpHeaders).length}`)

      // 创建HTTP请求
      const httpRequest = http.createHttp()

      // 设置请求选项
      const options: http.HttpRequestOptions = {
        method: http.RequestMethod.HEAD,  // 使用HEAD方法只获取响应头
        header: this.dlnaHttpHeaders as Record<string, string>,
        connectTimeout: 10000,
        readTimeout: 10000
      } as http.HttpRequestOptions

      hilog.info(DOMAIN, TAG, `发送HEAD请求到: ${this.dlnaUri.substring(0, 80)}...`)

      // 发送请求
      const response = await httpRequest.request(this.dlnaUri, options) as http.HttpResponse

      hilog.info(DOMAIN, TAG, `收到响应，状态码: ${response.responseCode}`)
      hilog.info(DOMAIN, TAG, `响应头: ${JSON.stringify(response.header)}`)

      // 格式化结果
      const status: number = response.responseCode
      const header = response.header as Record<string, string>
      const contentType: string = header['Content-Type'] || header['content-type'] || 'unknown'
      const contentLength: string = header['Content-Length'] || header['content-length'] || 'unknown'

      if (status >= 200 && status < 300) {
        this.httpTestResult = `✅ 请求成功!\n状态码: ${status}\n类型: ${contentType}\n大小: ${contentLength}\n\n开始播放视频...`

        // 延迟500ms后自动播放视频
        setTimeout(() => {
          this.playVideo()
        }, 500)
      } else if (status === 403) {
        this.httpTestResult = `❌ 403 禁止访问\n可能原因:\n1. HTTP头不正确\n2. URL已过期\n3. 反爬虫验证失败`
      } else if (status === 404) {
        this.httpTestResult = `❌ 404 文件不存在\nURL已失效或过期`
      } else {
        this.httpTestResult = `⚠️ 状态码: ${status}\n${contentType}`
      }

      httpRequest.destroy()
    } catch (error) {
      hilog.error(DOMAIN, TAG, `HTTP请求失败: ${JSON.stringify(error)}`)
      this.httpTestResult = `❌ 请求失败\n${JSON.stringify(error)}`
    } finally {
      this.httpTestLoading = false
    }
  }

  /**
   * 暂停视频
   */
  private async pauseVideo(): Promise<void> {
    try {
      await this.videoPlayer.pause()
      this.dlnaIsPlaying = false
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
   * 跳转
   */
  private async seekVideo(position: number): Promise<void> {
    try {
      await this.videoPlayer.seek(position)
      this.dlnaPosition = position
    } catch (error) {
      hilog.error(DOMAIN, TAG, `跳转失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 清理资源
   */
  private cleanup(): void {
    // 停止所有服务
    this.ssdpServer.stop()
    this.dlnaHttpServer.stop()
    this.phoneDiscoveryResponse.stop()
    this.syncClient.disconnect()
    this.videoPlayer.release()

    // 断开车机端连接
    this.carWebSocketClient.disconnect()
  }

  /**
   * 格式化时间
   */
  private formatTime(ms: number): string {
    let totalSeconds = Math.max(0, Math.floor(ms / 1000))
    let hours = Math.floor(totalSeconds / 3600)
    let minutes = Math.floor((totalSeconds % 3600) / 60)
    let seconds = totalSeconds % 60
    if (hours > 0) {
      return `${hours}:${this.pad2(minutes)}:${this.pad2(seconds)}`
    }
    return `${this.pad2(minutes)}:${this.pad2(seconds)}`
  }

  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }

  // ==================== UI 构建 ====================

  build() {
    Scroll() {
      Column({ space: 12 }) {
        // 标题栏
        this.TitleBar()

        // DLNA DMR状态卡片
        this.DlnaDmrStatusCard()

        // 播放模式选择
        this.PlaybackModeCard()

        // 播放控制卡片
        if (this.dlnaUri.length > 0) {
          this.PlaybackControlCard()

          // 独立视频播放测试卡片
          this.TestVideoPlayerCard()
        }

        // 同步状态卡片
        if (this.syncConnected) {
          this.SyncStatusCard()
        }

        // 服务状态卡片
        this.ServiceStatusCard()

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

  // ===== 标题栏 =====
  @Builder
  TitleBar() {
    Row() {
      Text('FSCast Remote')
        .fontSize(22)
        .fontWeight(FontWeight.Bold)
        .fontColor('#F1F5F9')
      Blank()
      // 刷新按钮
      Button() {
        Text('🔄')
          .fontSize(16)
      }
      .width(32)
      .height(32)
      .backgroundColor('#1E293B')
      .borderRadius(16)
      .type(ButtonType.Normal)
      .onClick(() => {
        hilog.info(DOMAIN, TAG, '用户点击刷新连接按钮')
        this.reconnectToCar()
      })
      Blank()
        .width(8)
      // 连接状态指示器
      Row({ space: 6 }) {
        Circle()
          .width(8)
          .height(8)
          .fill(this.webSocketConnected ? '#10B981' : '#64748B')
        Text(this.carConnectionStatus)
          .fontSize(12)
          .fontColor(this.webSocketConnected ? '#10B981' : '#64748B')
      }
      .padding({ left: 8, right: 8, top: 4, bottom: 4 })
      .backgroundColor('#1E293B')
      .borderRadius(12)
      Blank()
        .width(8)
      Text('DLNA DMR')
        .fontSize(13)
        .fontColor('#6366F1')
    }
    .width('100%')
    .height(48)
    .alignItems(VerticalAlign.Center)
  }

  // ===== DLNA DMR状态卡片 =====
  @Builder
  DlnaDmrStatusCard() {
    Column() {
      Text('DLNA接收状态')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')

      if (this.dlnaUri.length > 0) {
        // 正在播放
        Column() {
          Row() {
            Text('▶️')
              .fontSize(14)
            Text('正在接收投屏')
              .fontSize(13)
              .fontColor('#10B981')
              .margin({ left: 12 })
          }
          .width('100%')

          // URL可编辑文本框
          TextInput({ text: this.dlnaUri })
            .fontSize(11)
            .fontColor('#94A3B8')
            .backgroundColor('#0F172A')
            .borderColor('#334155')
            .borderRadius(8)
            .height(60)
            .margin({ top: 8 })
            .onChange((value: string) => {
              this.dlnaUri = value
            })
        }
        .width('100%')
        .padding(12)
        .backgroundColor('#1E293B')
        .borderRadius(10)
      } else {
        // 等待投屏
        Row() {
          Text('⏸')
            .fontSize(14)
          Text('等待DLNA投屏...')
            .fontSize(13)
            .fontColor('#94A3B8')
        }
        .width('100%')
        .padding(12)
        .backgroundColor('#1E293B')
        .borderRadius(10)
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
  }

  // ===== 播放模式选择卡片 =====
  @Builder
  PlaybackModeCard() {
    Column() {
      Text('播放模式')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')

      Row() {
        // 仅音频模式
        Column() {
          Text('🔊')
            .fontSize(24)
          Text('仅音频')
            .fontSize(13)
            .fontColor(this.playbackMode === 'audio_only' ? '#FFFFFF' : '#94A3B8')
            .margin({ top: 4 })
          if (this.playbackMode === 'audio_only') {
            Text('✓')
              .fontSize(14)
              .fontColor('#FFFFFF')
              .fontWeight(FontWeight.Bold)
          }
        }
        .width('48%')
        .padding(14)
        .borderRadius(12)
        .backgroundColor(this.playbackMode === 'audio_only' ? '#6366F1' : '#334155')
        .onClick(() => {
          this.playbackMode = 'audio_only'
          this.videoPlayer.setPlaybackMode('audio_only')
        })

        // 视频+音频模式
        Column() {
          Text('🎬')
            .fontSize(24)
          Text('视频+音频')
            .fontSize(13)
            .fontColor(this.playbackMode === 'video_audio' ? '#FFFFFF' : '#94A3B8')
            .margin({ top: 4 })
          if (this.playbackMode === 'video_audio') {
            Text('✓')
              .fontSize(14)
              .fontColor('#FFFFFF')
              .fontWeight(FontWeight.Bold)
          }
        }
        .width('48%')
        .padding(14)
        .borderRadius(12)
        .backgroundColor(this.playbackMode === 'video_audio' ? '#10B981' : '#334155')
        .onClick(() => {
          this.playbackMode = 'video_audio'
          this.videoPlayer.setPlaybackMode('video_audio')
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
  }

  // ===== 播放控制卡片 =====
  @Builder
  PlaybackControlCard() {
    Column() {
      // 标题
      Row() {
        Text('播放控制')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#F1F5F9')
        Blank()
        // 播放状态指示
        Row() {
          Circle()
            .width(8)
            .height(8)
            .fill(this.dlnaIsPlaying ? '#10B981' : '#64748B')
          Text(this.dlnaIsPlaying ? '播放中' : '已暂停')
            .fontSize(12)
            .fontColor(this.dlnaIsPlaying ? '#10B981' : '#94A3B8')
            .margin({ left: 6 })
        }
      }
      .width('100%')
      .margin({ bottom: 16 })

      // 视频显示区域 - 使用Video组件
      if (this.dlnaUri.length > 0) {
        Video({
          src: this.dlnaUri,
          controller: this.mainVideoController
        })
          .width('100%')
          .aspectRatio(16 / 9)
          .backgroundColor('#000000')
          .borderRadius(12)
          .autoPlay(true)  // 自动播放
          .controls(true)  // 显示控制条
          .loop(false)
          .muted(false)
          .objectFit(ImageFit.Contain)
          .onStart(() => {
            hilog.info(DOMAIN, TAG, '主视频开始播放')
            this.dlnaIsPlaying = true
          })
          .onPause(() => {
            hilog.info(DOMAIN, TAG, '主视频暂停')
            this.dlnaIsPlaying = false
          })
          .onFinish(() => {
            hilog.info(DOMAIN, TAG, '主视频播放完成')
            this.dlnaIsPlaying = false
          })
          .onError((error) => {
            hilog.error(DOMAIN, TAG, `主视频播放错误: ${JSON.stringify(error)}`)
          })
          .onPrepared((info: PreparedInfo) => {
            hilog.info(DOMAIN, TAG, `主视频准备完成，时长: ${info.duration}ms`)
            this.dlnaDuration = info.duration
            this.mainVideoPrepared = true
          })
          .onSeeking((time) => {
            hilog.info(DOMAIN, TAG, `主视频跳转到: ${time}ms`)
          })
          .onSeeked((info: PlaybackInfo) => {
            hilog.info(DOMAIN, TAG, `主视频跳转完成: ${info.time}ms`)
            this.dlnaPosition = info.time
          })
          .onUpdate((info: PlaybackInfo) => {
            // 更新手机实际播放进度
            this.phoneActualPosition = info.time
          })
          .margin({ bottom: 16 })
      } else {
        // 等待URL
        Text('等待接收投屏URL...')
          .width('100%')
          .aspectRatio(16 / 9)
          .fontSize(14)
          .fontColor('#94A3B8')
          .backgroundColor('#000000')
          .borderRadius(12)
          .textAlign(TextAlign.Center)
          .margin({ bottom: 16 })
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
    .margin({ top: 12 })
  }

  // ===== 独立视频播放测试卡片 =====
  @Builder
  TestVideoPlayerCard() {
    Column() {
      Text('视频播放测试')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')
        .margin({ bottom: 12 })

      // 视频显示区域 - 使用Video组件
      if (this.dlnaUri.length > 0) {
        Video({
          src: this.dlnaUri,
          controller: this.testVideoController
        })
          .width('100%')
          .aspectRatio(16 / 9)
          .backgroundColor('#000000')
          .borderRadius(12)
          .autoPlay(false)
          .controls(true)
          .loop(false)
          .muted(false)
          .objectFit(ImageFit.Contain)
          .onStart(() => {
            hilog.info(DOMAIN, TAG, '视频开始播放')
            this.testVideoPlaying = true
          })
          .onPause(() => {
            hilog.info(DOMAIN, TAG, '视频暂停')
            this.testVideoPlaying = false
          })
          .onFinish(() => {
            hilog.info(DOMAIN, TAG, '视频播放完成')
            this.testVideoPlaying = false
          })
          .onError((error) => {
            hilog.error(DOMAIN, TAG, `视频播放错误: ${JSON.stringify(error)}`)
          })
          .onPrepared((info: PreparedInfo) => {
            hilog.info(DOMAIN, TAG, `视频准备完成，时长: ${info.duration}ms`)
            this.testVideoDuration = info.duration
          })
          .onSeeking((time) => {
            hilog.info(DOMAIN, TAG, `视频跳转到: ${time}ms`)
          })
          .onSeeked((info: PlaybackInfo) => {
            hilog.info(DOMAIN, TAG, `视频跳转完成: ${info.time}ms`)
            this.testVideoProgress = info.time
          })
          .onUpdate((info: PlaybackInfo) => {
            this.testVideoProgress = info.time
          })
          .margin({ bottom: 12 })
      } else {
        // 等待URL
        Text('等待接收投屏URL...')
          .width('100%')
          .aspectRatio(16 / 9)
          .fontSize(14)
          .fontColor('#94A3B8')
          .backgroundColor('#000000')
          .borderRadius(12)
          .textAlign(TextAlign.Center)
          .margin({ bottom: 12 })
      }

      // 播放控制提示
      Text('使用视频播放器内置控制条播放')
        .fontSize(12)
        .fontColor('#94A3B8')
        .width('100%')
        .textAlign(TextAlign.Center)
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
    .margin({ top: 12 })
  }

  // ===== 同步状态卡片 =====
  @Builder
  SyncStatusCard() {
    Column() {
      Text('车机同步')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')

      Row() {
        Text('🔗')
          .fontSize(14)
        Text(`已连接: ${this.carIpAddress}`)
          .fontSize(13)
          .fontColor('#10B981')
          .margin({ left: 12 })
      }
      .width('100%')
      .padding(12)
      .backgroundColor('#1E293B')
      .borderRadius(10)
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
    .margin({ top: 12 })
  }

  // ===== 服务状态卡片 =====
  @Builder
  ServiceStatusCard() {
    Column() {
      Text('服务状态')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')

      Row() {
        Text('SSDP:')
          .fontSize(13)
          .fontColor('#94A3B8')
        Text(this.ssdpRunning ? '运行中' : '已停止')
          .fontSize(13)
          .fontColor(this.ssdpRunning ? '#10B981' : '#64748B')
          .margin({ left: 8 })
        Blank()
        Text('HTTP:')
          .fontSize(13)
          .fontColor('#94A3B8')
        Text(this.dlnaHttpRunning ? '运行中' : '已停止')
          .fontSize(13)
          .fontColor(this.dlnaHttpRunning ? '#10B981' : '#64748B')
          .margin({ left: 8 })
        Blank()
        Text('发现响应:')
          .fontSize(13)
          .fontColor('#94A3B8')
        Text(this.discoveryResponseRunning ? '运行中' : '已停止')
          .fontSize(13)
          .fontColor(this.discoveryResponseRunning ? '#10B981' : '#64748B')
      }
      .width('100%')
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
    .margin({ top: 12 })
  }
}
