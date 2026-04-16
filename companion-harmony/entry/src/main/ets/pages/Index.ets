/**
 * FSCast Remote 主页面
 * DLNA DMR + 设备发现响应 + 同步客户端
 */
import { router, window } from '@kit.ArkUI'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { preferences } from '@kit.ArkData'
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
  private carIpAddress: string = '192.168.31.176'  // 默认车机IP，用户可以修改

  // ===== DLNA DMR状态 =====
  @State @Watch('onDlnaUriChanged') dlnaUri: string = ''  // 接收到的DLNA播放URI
  @State dlnaHttpHeaders: Record<string, string> = {}  // 接收到的HTTP头
  @State dlnaIsPlaying: boolean = false
  @State dlnaPosition: number = 0
  @State dlnaDuration: number = 0

  // ===== 同步状态 =====
  @State syncConnected: boolean = false

  // ===== 播放模式 =====
  @State playbackMode: 'audio_only' | 'video_audio' = 'audio_only'

  // ===== 服务运行状态 =====
  @State ssdpRunning: boolean = false
  @State dlnaHttpRunning: boolean = false
  @State discoveryResponseRunning: boolean = false

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
      hilog.info(DOMAIN, TAG, `收到播放命令: ${uri.substring(0, 50)}...`)
      hilog.info(DOMAIN, TAG, `HTTP头: ${JSON.stringify(httpHeaders)}`)

      // 更新状态
      this.dlnaUri = uri
      this.dlnaHttpHeaders = httpHeaders

      // 播放视频
      this.playVideo()
    })

    this.carWebSocketClient.setOnPauseCommand(() => {
      hilog.info(DOMAIN, TAG, '收到暂停命令')
      this.pauseVideo()
    })

    this.carWebSocketClient.setOnStopCommand(() => {
      hilog.info(DOMAIN, TAG, '收到停止命令')
      this.stopVideo()
    })

    this.carWebSocketClient.setOnSeekCommand((position: number) => {
      hilog.info(DOMAIN, TAG, `收到跳转命令: ${position}`)
      this.seekVideo(position)
    })

    // 连接到车机端
    this.carWebSocketClient.connect(this.carIpAddress)
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
   * 监听DLNA URI变化
   */
  onDlnaUriChanged(): void {
    if (this.dlnaUri.length > 0) {
      hilog.info(DOMAIN, TAG, `收到DLNA播放URI: ${this.dlnaUri.substring(0, 100)}...`)
      // 自动开始播放
      this.playVideo()
    }
  }

  aboutToDisappear(): void {
    hilog.info(DOMAIN, TAG, 'Page aboutToDisappear')
    this.cleanup()
  }

  /**
   * 播放视频
   */
  private async playVideo(): Promise<void> {
    if (this.dlnaUri.length === 0) {
      hilog.warn(DOMAIN, TAG, '无播放URI')
      return
    }

    try {
      hilog.info(DOMAIN, TAG, `开始播放视频，HTTP头数量: ${Object.keys(this.dlnaHttpHeaders).length}`)

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
        Row() {
          Text('▶️')
            .fontSize(14)
          Column() {
            Text('正在接收投屏')
              .fontSize(13)
              .fontColor('#10B981')
            Text(this.dlnaUri.substring(0, 50) + '...')
              .fontSize(11)
              .fontColor('#94A3B8')
              .margin({ top: 4 })
          }
          .alignItems(HorizontalAlign.Start)
          .margin({ left: 12 })
          .layoutWeight(1)
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

      // 进度条
      Row() {
        Text(this.formatTime(this.dlnaPosition))
          .fontSize(12)
          .fontColor('#94A3B8')
          .fontFamily('monospace')
          .width(50)
        Slider({
          value: this.dlnaPosition,
          min: 0,
          max: Math.max(this.dlnaDuration, 1),
          step: 1000,
          style: SliderStyle.OutSet
        })
          .layoutWeight(1)
          .trackColor('#334155')
          .selectedColor('#6366F1')
          .blockColor('#8B5CF6')
          .onChange((value: number) => {
            this.seekVideo(value)
          })
        Text(this.formatTime(this.dlnaDuration))
          .fontSize(12)
          .fontColor('#94A3B8')
          .fontFamily('monospace')
          .width(50)
          .textAlign(TextAlign.End)
      }
      .width('100%')
      .margin({ bottom: 16 })

      // 控制按钮
      Row() {
        // 播放/暂停
        Button() {
          Text(this.dlnaIsPlaying ? '❚❚' : '▶')
            .fontSize(20)
            .fontColor('#F1F5F9')
        }
        .width(50)
        .height(50)
        .borderRadius(25)
        .backgroundColor('#334155')
        .type(ButtonType.Normal)
        .onClick(() => {
          if (this.dlnaIsPlaying) {
            this.pauseVideo()
          } else {
            this.playVideo()
          }
        })

        Blank().width(12)

        // 停止
        Button() {
          Text('■')
            .fontSize(20)
            .fontColor('#F1F5F9')
        }
        .width(50)
        .height(50)
        .borderRadius(25)
        .backgroundColor('#334155')
        .type(ButtonType.Normal)
        .onClick(() => {
          this.stopVideo()
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
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
