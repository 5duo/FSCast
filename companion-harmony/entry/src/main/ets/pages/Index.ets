/**
 * FSCast Remote 主页面
 * 连接管理 + 播放控制 + 音频输出选择 + 车机控制
 */
import { router, window } from '@kit.ArkUI'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { preferences } from '@kit.ArkData'
import {
  TCP_PORT, PLATFORM
} from '../common/Constants'
import {
  DiscoverInfo, PlaybackState, ConnectionInfo, ConnectionState, AudioFormat
} from '../model/Models'
import { DiscoveryClient } from '../service/DiscoveryClient'
import { AudioStreamClient } from '../service/AudioStreamClient'
import { AudioPlayer } from '../service/AudioPlayer'
import { BackgroundService } from '../service/BackgroundService'
import { BluetoothMonitor } from '../bluetooth/BluetoothMonitor'
import { MediaSessionService } from '../service/MediaSessionService'

const TAG = 'IndexPage'
const DOMAIN = 0x0000
const PREF_KEY_LAST_IP = 'last_connected_ip'
const TEST_MODE_IP = '1.1.1.1'  // 测试模式 IP

@Entry
@Component
struct Index {
  // ===== 连接状态 =====
  @State connectionState: number = ConnectionState.DISCONNECTED
  @State serverIp: string = ''
  @State serverName: string = ''
  @State discoverResults: DiscoverInfo[] = []
  @State isDiscovering: boolean = false
  @State manualIp: string = ''

  // ===== 播放状态 =====
  @State isPlaying: boolean = false
  @State positionMs: number = 0
  @State durationMs: number = 0
  @State title: string = ''

  // ===== 音频输出 =====
  @State audioOutput: string = 'speaker'  // 'speaker' 或 'phone'
  @State isReceivingPcm: boolean = false
  // ===== 屏幕常亮设置 =====
  @State keepScreenOn: boolean = true

  // ===== 蓝牙 =====
  @State bluetoothConnected: boolean = false
  @State bluetoothDeviceName: string = ''

  // ===== 服务实例 =====
  private discoveryClient: DiscoveryClient = new DiscoveryClient()
  private streamClient: AudioStreamClient = new AudioStreamClient()
  private audioPlayer: AudioPlayer = new AudioPlayer()
  private backgroundService: BackgroundService = new BackgroundService()
  private bluetoothMonitor: BluetoothMonitor = new BluetoothMonitor()
  private mediaSessionService: MediaSessionService = new MediaSessionService()

  // ===== 测试模式模拟播放 =====
  private testModeTimer: number = -1
  private testModePosition: number = 0
  private readonly TEST_MODE_DURATION = 180000  // 3分钟

  // ===== 进度条拖动状态 =====
  @State isSeeking: boolean = false
  @State seekPosition: number = 0

  aboutToAppear(): void {
    hilog.info(DOMAIN, TAG, 'Page aboutToAppear')

    // 设置上下文
    this.backgroundService.setContext(this.getUIContext().getHostContext() as Context)
    this.mediaSessionService.setContext(this.getUIContext().getHostContext() as Context)

    // 初始化回调（同步）
    this.initStreamCallbacks()

    // 初始化媒体会话回调
    this.initMediaSessionCallbacks()

    // 异步初始化（不阻塞）
    this.initializeAsync()
  }

  /**
   * 异步初始化（不阻塞 UI）
   */
  private async initializeAsync(): Promise<void> {
    try {
      // 初始化蓝牙监听（优先）
      await this.initBluetoothCallbacks()
      hilog.info(DOMAIN, TAG, 'Bluetooth callbacks initialized')
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'Bluetooth init failed: ' + JSON.stringify(e))
    }

    try {
      // 自动连接历史设备（非阻塞）
      await this.autoConnectLastDevice()
      hilog.info(DOMAIN, TAG, 'Auto-connect completed')
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'Auto-connect failed: ' + JSON.stringify(e))
    }
  }

  /**
   * 自动连接最后使用的设备（非阻塞）
   */
  private async autoConnectLastDevice(): Promise<void> {
    try {
      // 从 preferences 读取最后连接的 IP
      let preferenceStore = await preferences.getPreferences(this.getUIContext().getHostContext() as Context, 'fscast_prefs')
      const lastIp = await preferenceStore.get(PREF_KEY_LAST_IP, '') as string

      if (lastIp && lastIp.length > 0) {
        hilog.info(DOMAIN, TAG, `Auto-connecting to last device: ${lastIp}`)

        // 非阻塞连接：立即返回，在后台尝试连接
        // 使用 setTimeout 将连接操作延后到下一个事件循环
        setTimeout(() => {
          this.doConnect(lastIp, TCP_PORT).catch((err: Error) => {
            // 连接失败不影响界面显示
            hilog.warn(DOMAIN, TAG, `Auto-connect failed: ${err.message}`)
          })
        }, 100)
      } else {
        hilog.info(DOMAIN, TAG, 'No last IP found, showing main UI')
      }
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'Auto connect failed: ' + JSON.stringify(e))
    }
  }

  /**
   * 保存连接的 IP
   */
  private async saveConnectedIp(ip: string): Promise<void> {
    try {
      let preferenceStore = await preferences.getPreferences(this.getUIContext().getHostContext() as Context, 'fscast_prefs')
      await preferenceStore.put(PREF_KEY_LAST_IP, ip)
      hilog.info(DOMAIN, TAG, `Saved last connected IP: ${ip}`)
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'Save IP failed: ' + JSON.stringify(e))
    }
  }

  aboutToDisappear(): void {
    hilog.info(DOMAIN, TAG, 'Page aboutToDisappear')
    this.cleanup()
  }

  /**
   * 初始化 TCP 流客户端回调
   */
  private initStreamCallbacks(): void {
    // 格式头 → 配置 AudioRenderer
    this.streamClient.onFormatHeader = (format: AudioFormat) => {
      hilog.info(DOMAIN, TAG, `Format header: ${format.sampleRate}Hz, ${format.channels}ch`)
      this.audioPlayer.configure(format).then(() => {
        if (this.audioOutput === 'phone') {
          this.audioPlayer.start()
        }
      })
    }

    // PCM 数据 → 写入 AudioPlayer（带时间戳）
    this.streamClient.onPcmData = (data: ArrayBuffer, timestamp: number) => {
      if (this.isReceivingPcm) {
        this.audioPlayer.writePcmData(data, timestamp)
      }
    }

    // 格式变化 → 重建 AudioRenderer 并清空缓冲区
    this.streamClient.onFormatChange = (format: AudioFormat) => {
      hilog.info(DOMAIN, TAG, `Format change: ${format.sampleRate}Hz, ${format.channels}ch - 清空旧音频队列`)
      this.audioPlayer.stop().then(() => {
        // 清空缓冲区，避免换视频时音画不同步
        this.audioPlayer.flush().then(() => {
          this.audioPlayer.configure(format).then(() => {
            if (this.audioOutput === 'phone') {
              this.audioPlayer.start()
            }
          })
        })
      })
    }

    // 状态同步 → 更新 UI
    this.streamClient.onStateUpdate = (state: PlaybackState) => {
      if (!this.isSeeking) {
        this.isPlaying = state.isPlaying
        this.positionMs = state.positionMs
        this.durationMs = state.durationMs
        this.title = state.title
      }
      this.audioOutput = state.audioOutput
      this.isReceivingPcm = state.audioOutput === 'phone'

      // 同步到媒体会话
      if (this.isReceivingPcm && this.mediaSessionService.isActive()) {
        this.mediaSessionService.setPlaybackState(
          this.isPlaying,
          this.positionMs,
          this.durationMs
        )
      }
    }

    // 命令结果
    this.streamClient.onCommandResult = (result) => {
      hilog.info(DOMAIN, TAG, `Command result: ${result.action} success=${result.success}`)
    }

    // 音频输出变化通知
    this.streamClient.onOutputChanged = async (output: string) => {
      hilog.info(DOMAIN, TAG, `Output changed: ${output}, isPlaying=${this.isPlaying}`)
      this.audioOutput = output
      this.isReceivingPcm = output === 'phone'
      if (output === 'phone') {
        // 启动 AudioPlayer 播放 PCM 流
        hilog.info(DOMAIN, TAG, 'Starting AudioPlayer')
        this.audioPlayer.start()

        // 暂时禁用后台服务，避免连接断开问题
        // TODO: 后续重新启用并调试
        /*
        // 创建媒体会话并启动后台播放（包含后台任务管理）
        if (!this.mediaSessionService.isActive()) {
          await this.mediaSessionService.createSession()
          await this.mediaSessionService.startBackgroundService()
          hilog.info(DOMAIN, TAG, 'MediaSession created and background service started')
        }

        // 设置媒体元数据
        this.mediaSessionService.setMetadata(this.title, 'FSCast', this.durationMs)

        // 延迟设置播放状态，等待 AudioPlayer 真正开始播放
        // 这样可以确保 AVSession 被正确激活
        setTimeout(() => {
          this.mediaSessionService.setPlaybackState(true, 0, this.durationMs)
          hilog.info(DOMAIN, TAG, 'Playback state set after delay for AVSession activation')
        }, 1000)  // 增加延迟到 1 秒
        */
      } else {
        this.audioPlayer.pause()

        // 释放媒体会话
        if (this.mediaSessionService.isActive()) {
          await this.mediaSessionService.release()
        }
      }
    }

    // 连接状态变化
    this.streamClient.onConnectionChanged = (state: number) => {
      this.connectionState = state
      if (state === ConnectionState.CONNECTED) {
        // 连接成功时，MediaSessionService 会在音频输出切换到 phone 时启动
      } else if (state === ConnectionState.DISCONNECTED) {
        this.isPlaying = false
        this.isReceivingPcm = false
        this.audioPlayer.stop()
        // 释放媒体会话和后台服务
        if (this.mediaSessionService.isActive()) {
          this.mediaSessionService.release()
        }
      }
    }

    // 设置 AudioPlayer 缓冲区大小变化回调 → 通知 AudioStreamClient 用于流量控制
    this.audioPlayer.onBufferSizeChanged = (currentSize: number, maxSize: number) => {
      this.streamClient.updateBufferSize(currentSize, maxSize)
    }
  }

  /**
   * 初始化蓝牙回调
   */
  private async initBluetoothCallbacks(): Promise<void> {
    this.bluetoothMonitor.onBluetoothConnected = (deviceName: string) => {
      this.bluetoothConnected = true
      this.bluetoothDeviceName = deviceName
      hilog.info(DOMAIN, TAG, `Bluetooth connected callback triggered: ${deviceName}`)
    }
    this.bluetoothMonitor.onBluetoothDisconnected = () => {
      this.bluetoothConnected = false
      this.bluetoothDeviceName = ''
      hilog.info(DOMAIN, TAG, 'Bluetooth disconnected callback triggered')
      // 蓝牙断开时自动暂停
      if (this.isReceivingPcm) {
        this.streamClient.sendPause()
      }
    }
    await this.bluetoothMonitor.startMonitoring()
  }

  /**
   * 初始化媒体会话回调
   */
  private initMediaSessionCallbacks(): void {
    // 播放控制
    this.mediaSessionService.onPlay = () => {
      this.streamClient.sendPlay()
    }

    this.mediaSessionService.onPause = () => {
      this.streamClient.sendPause()
    }

    this.mediaSessionService.onStop = () => {
      this.streamClient.sendStop()
    }

    this.mediaSessionService.onPlayNext = () => {
      // 车机端暂不支持下一首
      hilog.info(DOMAIN, TAG, 'Play next not supported')
    }

    this.mediaSessionService.onPlayPrevious = () => {
      // 车机端暂不支持上一首
      hilog.info(DOMAIN, TAG, 'Play previous not supported')
    }

    this.mediaSessionService.onSeek = (position: number) => {
      this.streamClient.sendSeek(position)
    }

    hilog.info(DOMAIN, TAG, 'Media session callbacks initialized')
  }

  // ==================== 用户操作 ====================

  /**
   * 扫描车机
   */
  private async doDiscover(): Promise<void> {
    this.isDiscovering = true
    this.discoverResults = await this.discoveryClient.discover(3000)
    this.isDiscovering = false
    hilog.info(DOMAIN, TAG, `Found ${this.discoverResults.length} devices`)
  }

  /**
   * 连接车机
   */
  private async doConnect(ip: string, port: number = TCP_PORT): Promise<void> {
    // 检查是否是测试模式 IP
    if (ip === TEST_MODE_IP) {
      hilog.info(DOMAIN, TAG, 'Test mode detected, entering simulated connection state')

      // 直接设置为连接成功状态（不进行实际连接）
      this.serverIp = ip
      this.connectionState = ConnectionState.CONNECTED

      // 模拟一些初始状态
      this.isPlaying = false
      this.positionMs = 0
      this.durationMs = this.TEST_MODE_DURATION
      this.title = '测试音频 - 示例音乐'
      this.audioOutput = 'speaker'
      this.isReceivingPcm = false

      // 启动测试模式模拟播放（会自动启动 MediaSessionService）
      await this.startTestModePlayback()

      hilog.info(DOMAIN, TAG, 'Test mode: simulated connection established')
      return
    }

    // 正常连接流程
    try {
      await this.streamClient.connect(ip, port)
      this.serverIp = ip
      // 发送握手确认
      await this.streamClient.sendHelloAck('FSCast Remote')
      // 保存连接的 IP（下次自动连接）
      await this.saveConnectedIp(ip)
      hilog.info(DOMAIN, TAG, 'Connected and handshake sent')
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'Connect failed: ' + (e as Error).message)
    }
  }

  /**
   * 断开连接
   */
  private async doDisconnect(): Promise<void> {
    // 检查是否是测试模式
    if (this.serverIp === TEST_MODE_IP) {
      hilog.info(DOMAIN, TAG, 'Test mode: disconnecting simulated connection')

      // 停止测试模式模拟播放
      if (this.testModeTimer !== -1) {
        clearInterval(this.testModeTimer)
        this.testModeTimer = -1
      }

      // 释放媒体会话
      if (this.mediaSessionService.isActive()) {
        await this.mediaSessionService.release()
      }

      // 测试模式断开：重置状态
      this.serverIp = ''
      this.connectionState = ConnectionState.DISCONNECTED
      this.isPlaying = false
      this.isReceivingPcm = false

      // 停止后台服务
      this.backgroundService.stop()

      hilog.info(DOMAIN, TAG, 'Test mode: disconnected')
      return
    }

    // 正常断开流程
    await this.streamClient.sendDisconnect()
    await this.streamClient.disconnect()
    this.audioPlayer.release()
    this.backgroundService.stop()
    this.serverIp = ''
    this.connectionState = ConnectionState.DISCONNECTED
  }

  /**
   * 清理资源
   */
  private cleanup(): void {
    // 停止测试模式定时器
    if (this.testModeTimer !== -1) {
      clearInterval(this.testModeTimer)
      this.testModeTimer = -1
    }

    this.discoveryClient.stop()
    this.streamClient.disconnect()
    this.audioPlayer.release()
    this.backgroundService.stop()
    this.bluetoothMonitor.stopMonitoring()
    this.mediaSessionService.release()
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
      Column() {
        // 标题栏
        this.TitleBar()

        // 连接面板
        this.ConnectionPanel()

        // 播放控制卡片
        if (this.connectionState === ConnectionState.CONNECTED) {
          this.PlaybackCard()
          this.AudioOutputCard()
          this.CarControlCard()
          this.SettingsCard()
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

  // ===== 标题栏 =====
  @Builder
  TitleBar() {
    Row() {
      Text('FSCast Remote')
        .fontSize(22)
        .fontWeight(FontWeight.Bold)
        .fontColor('#F1F5F9')
      Blank()
      Text(this.connectionState === ConnectionState.CONNECTED ? '已连接' :
        this.connectionState === ConnectionState.CONNECTING ? '连接中...' :
          this.connectionState === ConnectionState.RECONNECTING ? '重连中...' : '未连接')
        .fontSize(13)
        .fontColor(this.connectionState === ConnectionState.CONNECTED ? '#10B981' : '#94A3B8')
    }
    .width('100%')
    .height(48)
    .alignItems(VerticalAlign.Center)
  }

  // ===== 连接面板 =====
  @Builder
  ConnectionPanel() {
    Column() {
      if (this.connectionState === ConnectionState.CONNECTED) {
        // 已连接状态
        Row() {
          Text('🔗')
            .fontSize(20)
          Column() {
            Text(`已连接: ${this.serverName || 'FSCast'}`)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor('#F1F5F9')
            Text(this.serverIp)
              .fontSize(13)
              .fontColor('#94A3B8')
          }
          .alignItems(HorizontalAlign.Start)
          .margin({ left: 12 })
          .layoutWeight(1)
          Button('断开')
            .fontSize(13)
            .fontColor('#EF4444')
            .backgroundColor('#334155')
            .borderRadius(8)
            .height(32)
            .onClick(() => this.doDisconnect())
        }
        .width('100%')
        .padding(16)
      } else {
        // 未连接状态
        Text('设备发现')
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor('#F1F5F9')
          .width('100%')
          .margin({ bottom: 12 })

        // 扫描按钮
        Button(this.isDiscovering ? '扫描中...' : '扫描车机')
          .width('100%')
          .height(44)
          .fontSize(15)
          .fontColor('#FFFFFF')
          .backgroundColor(this.isDiscovering ? '#334155' : '#6366F1')
          .borderRadius(10)
          .enabled(!this.isDiscovering)
          .onClick(() => this.doDiscover())

        // 发现结果列表
        if (this.discoverResults.length > 0) {
          ForEach(this.discoverResults, (item: DiscoverInfo) => {
            Row() {
              Column() {
                Text(item.name)
                  .fontSize(15)
                  .fontColor('#F1F5F9')
                Text(item.ip)
                  .fontSize(12)
                  .fontColor('#94A3B8')
              }
              .alignItems(HorizontalAlign.Start)
              .layoutWeight(1)
              Button('连接')
                .fontSize(13)
                .fontColor('#6366F1')
                .backgroundColor('#1E293B')
                .border({ width: 1, color: '#6366F1', radius: 8 })
                .height(32)
                .onClick(() => this.doConnect(item.ip, item.port))
            }
            .width('100%')
            .padding(12)
            .margin({ top: 8 })
            .backgroundColor('#1E293B')
            .borderRadius(10)
          }, (item: DiscoverInfo) => item.ip)
        } else if (!this.isDiscovering) {
          // 手动输入 IP
          Row() {
            TextInput({ placeholder: '手动输入 IP 地址', text: this.manualIp })
              .fontSize(14)
              .fontColor('#F1F5F9')
              .placeholderColor('#64748B')
              .backgroundColor('#1E293B')
              .borderRadius(8)
              .layoutWeight(1)
              .height(40)
              .onChange((value: string) => { this.manualIp = value })
            Button('连接')
              .fontSize(13)
              .fontColor('#FFFFFF')
              .backgroundColor('#6366F1')
              .borderRadius(8)
              .height(40)
              .margin({ left: 8 })
              .enabled(this.manualIp.length > 0)
              .onClick(() => this.doConnect(this.manualIp))
          }
          .width('100%')
          .margin({ top: 12 })
        }
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
  }

  // ===== 播放控制卡片 =====
  @Builder
  PlaybackCard() {
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
            .fill(this.isPlaying ? '#10B981' : '#64748B')
          Text(this.isPlaying ? '播放中' : (this.durationMs > 0 ? '已暂停' : '无内容'))
            .fontSize(12)
            .fontColor(this.isPlaying ? '#10B981' : '#94A3B8')
            .margin({ left: 6 })
        }
      }
      .width('100%')
      .margin({ bottom: 16 })

      // 当前播放标题
      if (this.title.length > 0) {
        Text(this.title)
          .fontSize(14)
          .fontColor('#94A3B8')
          .width('100%')
          .margin({ bottom: 12 })
      }

      // 进度条
      Row() {
        Text(this.formatTime(this.isSeeking ? this.seekPosition : this.positionMs))
          .fontSize(12)
          .fontColor('#94A3B8')
          .fontFamily('monospace')
          .width(50)
        Slider({
          value: this.isSeeking ? this.seekPosition : this.positionMs,
          min: 0,
          max: Math.max(this.durationMs, 1),
          step: 1000,
          style: SliderStyle.OutSet
        })
          .layoutWeight(1)
          .trackColor('#334155')
          .selectedColor('#6366F1')
          .blockColor('#8B5CF6')
          .onChange((value: number) => {
            this.isSeeking = true
            this.seekPosition = value
          })
          .onTouch((event: TouchEvent) => {
            if (event.type === TouchType.Up && this.isSeeking) {
              this.streamClient.sendSeek(this.seekPosition)
              this.isSeeking = false
            }
          })
        Text(this.formatTime(this.durationMs))
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
        // 上一集
        this.ControlButton('⏮', '上一集', () => {
          // 车机端暂不支持，可通过 seek 实现
          this.streamClient.sendSeek(0)
        })

        Blank().width(12)

        // 播放/暂停
        this.ControlButton(this.isPlaying ? '❚❚' : '▶', this.isPlaying ? '暂停' : '播放', () => {
          if (this.serverIp === TEST_MODE_IP) {
            // 测试模式：切换播放/暂停状态
            this.isPlaying = !this.isPlaying
            if (this.isPlaying) {
              // 恢复播放
              if (this.testModeTimer === -1) {
                // 异步启动播放（不等待）
                this.startTestModePlayback()
              }
            } else {
              // 暂停：不清除定时器，只更新状态
              this.mediaSessionService.setPlaybackState(false, this.testModePosition, this.durationMs)
            }
          } else {
            // 正常模式：发送命令给车机
            if (this.isPlaying) {
              this.streamClient.sendPause()
            } else {
              this.streamClient.sendPlay()
            }
          }
        })

        Blank().width(12)

        // 停止
        this.ControlButton('■', '停止', () => {
          this.streamClient.sendStop()
        })

        Blank().width(12)

        // 下一集
        this.ControlButton('⏭', '下一集', () => {
          // 车机端暂不支持
        })

        Blank().width(12)

        // 音频输出按钮
        this.ControlButton(this.isReceivingPcm ? '🔊' : '🔇', this.isReceivingPcm ? '有声' : '静音', () => {
          if (this.isReceivingPcm) {
            this.streamClient.sendSetAudioOutput('speaker')
          } else {
            this.streamClient.sendSetAudioOutput('phone')
          }
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

  // ===== 控制按钮组件 =====
  @Builder
  ControlButton(icon: string, desc: string, action: () => void) {
    Button() {
      Text(icon)
        .fontSize(20)
        .fontColor('#F1F5F9')
    }
    .width(50)
    .height(50)
    .borderRadius(25)
    .backgroundColor('#334155')
    .type(ButtonType.Normal)
    .onClick(action)
  }

  // ===== 音频输出选择卡片 =====
  @Builder
  AudioOutputCard() {
    Column() {
      Text('音频输出')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')
        .margin({ bottom: 12 })

      Row() {
        // 手机/蓝牙
        Column() {
          Text('📱')
            .fontSize(24)
          Text('手机/蓝牙')
            .fontSize(13)
            .fontColor(this.audioOutput === 'phone' ? '#FFFFFF' : '#94A3B8')
            .margin({ top: 4 })
          if (this.audioOutput === 'phone') {
            Text('✓')
              .fontSize(14)
              .fontColor('#FFFFFF')
              .fontWeight(FontWeight.Bold)
          }
        }
        .width('48%')
        .padding(14)
        .borderRadius(12)
        .backgroundColor(this.audioOutput === 'phone' ? '#6366F1' : '#334155')
        .border({
          width: 1,
          color: this.audioOutput === 'phone' ? '#6366F1' : '#334155',
          radius: 12
        })
        .onClick(() => {
          this.streamClient.sendSetAudioOutput('phone')
        })

        // 车机扬声器
        Column() {
          Text('🔈')
            .fontSize(24)
          Text('车机扬声器')
            .fontSize(13)
            .fontColor(this.audioOutput === 'speaker' ? '#FFFFFF' : '#94A3B8')
            .margin({ top: 4 })
          if (this.audioOutput === 'speaker') {
            Text('✓')
              .fontSize(14)
              .fontColor('#FFFFFF')
              .fontWeight(FontWeight.Bold)
          }
        }
        .width('48%')
        .padding(14)
        .borderRadius(12)
        .backgroundColor(this.audioOutput === 'speaker' ? '#10B981' : '#334155')
        .border({
          width: 1,
          color: this.audioOutput === 'speaker' ? '#10B981' : '#334155',
          radius: 12
        })
        .onClick(() => {
          this.streamClient.sendSetAudioOutput('speaker')
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)

      // 蓝牙状态
      Row() {
        Text(this.bluetoothConnected ? '🎧' : '📡')
          .fontSize(14)
        if (this.bluetoothConnected && this.bluetoothDeviceName.length > 0) {
          Text(`蓝牙: ${this.bluetoothDeviceName}`)
            .fontSize(12)
            .fontColor(this.audioOutput === 'phone' ? '#6366F1' : '#10B981')
            .margin({ left: 6 })
        } else {
          Text(this.bluetoothConnected ? '蓝牙耳机已连接' : '未连接蓝牙耳机')
            .fontSize(12)
            .fontColor(this.bluetoothConnected ? '#10B981' : '#64748B')
            .margin({ left: 6 })
        }

        if (this.bluetoothConnected) {
          // 显示当前输出状态
          Text(this.audioOutput === 'phone' ? '(播放中)' : '(已暂停)')
            .fontSize(10)
            .fontColor(this.audioOutput === 'phone' ? '#6366F1' : '#64748B')
            .margin({ left: 4 })
        }

        Blank()

        if (this.bluetoothConnected) {
          // 切换按钮
          Text(this.audioOutput === 'phone' ? '切换到扬声器' : '切换到蓝牙')
            .fontSize(10)
            .fontColor('#FFFFFF')
            .padding({ left: 8, right: 8, top: 4, bottom: 4 })
            .borderRadius(4)
            .backgroundColor('#6366F1')
            .onClick(() => {
              this.toggleAudioOutput()
            })
        }
      }
      .width('100%')
      .padding(12)
      .borderRadius(8)
      .backgroundColor('#1E293B')
      .margin({ top: 12 })
      .onClick(() => {
        if (this.bluetoothConnected) {
          this.toggleAudioOutput()
        }
      })
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
    .margin({ top: 12 })
  }

  // ===== 车机控制卡片 =====
  @Builder
  CarControlCard() {
    Column() {
      Text('车机投屏')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')
        .margin({ bottom: 12 })

      Button('停止投屏')
        .width('100%')
        .height(44)
        .fontSize(15)
        .fontColor('#EF4444')
        .backgroundColor('#EF444420')
        .border({ width: 1, color: '#EF444450', radius: 10 })
        .onClick(() => {
          this.streamClient.sendStop()
        })
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
    .margin({ top: 12 })
  }

  // ===== 设置卡片 =====
  @Builder
  SettingsCard() {
    Column() {
      Text('设置')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')
        .margin({ bottom: 12 })

      Row() {
        Column() {
          Text('保持屏幕常亮')
            .fontSize(14)
            .fontColor('#F1F5F9')
          Text('防止手机锁屏，音频播放时推荐开启')
            .fontSize(12)
            .fontColor('#94A3B8')
            .margin({ top: 4 })
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)

        Toggle({ type: ToggleType.Switch, isOn: this.keepScreenOn })
          .selectedColor('#6366F1')
          .onChange((isOn: boolean) => {
            this.keepScreenOn = isOn
            this.setKeepScreenOn(isOn)
          })
      }
      .width('100%')
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
    .margin({ top: 12 })
  }

  /**
   * 设置屏幕常亮（注意：实际控制由 EntryAbility 处理）
   */
  private setKeepScreenOn(keepOn: boolean): void {
    hilog.info(DOMAIN, TAG, `User requested keep screen on: ${keepOn}`)
    // 在页面中无法直接控制窗口，需要在 Ability 层处理
    // EntryAbility 中已默认启用屏幕常亮
  }

  /**
   * 切换音频输出
   */
  private toggleAudioOutput(): void {
    if (this.audioOutput === 'phone') {
      // 当前是手机/蓝牙播放，切换到扬声器
      this.streamClient.sendSetAudioOutput('speaker')
      hilog.info(DOMAIN, TAG, 'Switch to speaker')
    } else {
      // 当前是扬声器，切换到手机/蓝牙播放
      this.streamClient.sendSetAudioOutput('phone')
      hilog.info(DOMAIN, TAG, 'Switch to phone/bluetooth')
    }
  }

  /**
   * 启动测试模式模拟播放
   */
  private async startTestModePlayback(): Promise<void> {
    hilog.info(DOMAIN, TAG, 'Starting test mode playback simulation')

    // 模拟正在播放
    this.isPlaying = true
    this.audioOutput = 'phone'
    this.isReceivingPcm = true

    // 创建媒体会话（等待完成）
    await this.mediaSessionService.createSession()
    hilog.info(DOMAIN, TAG, 'AVSession created')

    // 启动后台服务
    await this.mediaSessionService.startBackgroundService()
    hilog.info(DOMAIN, TAG, 'Background service started')

    // 设置媒体元数据
    this.mediaSessionService.setMetadata(this.title, 'FSCast 测试', this.durationMs)
    this.mediaSessionService.setPlaybackState(true, 0, this.durationMs)

    // 启动进度模拟（每秒更新一次进度）
    this.testModePosition = 0
    this.testModeTimer = setInterval(() => {
      this.testModePosition += 1000  // 每秒增加1秒

      // 更新播放状态
      this.mediaSessionService.setPlaybackState(
        this.isPlaying,
        this.testModePosition,
        this.durationMs
      )

      // 更新UI
      this.positionMs = this.testModePosition

      hilog.debug(DOMAIN, TAG, `Test mode: position=${this.testModePosition}ms`)

      // 模拟播放完成
      if (this.testModePosition >= this.durationMs) {
        this.isPlaying = false
        this.mediaSessionService.setPlaybackState(false, this.testModePosition, this.durationMs)
        clearInterval(this.testModeTimer)
        hilog.info(DOMAIN, TAG, 'Test mode: playback finished')
      }
    }, 1000)
  }
}
