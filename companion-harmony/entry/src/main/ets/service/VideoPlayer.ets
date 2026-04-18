/**
 * 视频播放器
 * 鸿蒙手机端DLNA DMR - 使用AVPlayer播放HTTP视频流
 * 支持视频+音频播放和进度同步，支持后台播放
 */
import media from '@ohos.multimedia.media'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { BusinessError } from '@kit.BasicServicesKit'

const DOMAIN = 0x0000
const TAG = 'VideoPlayer'

export class VideoPlayer {
  private avPlayer: media.AVPlayer | null = null
  private surfaceId: string = ''
  private currentUri: string = ''
  private isPlaying: boolean = false
  private currentPosition: number = 0
  private duration: number = 0

  // 进度同步优化相关
  private isPhoneMode: boolean = false
  private seekDebounceTimer: number = -1
  private readonly SEEK_DEBOUNCE_DELAY = 300

  // 事件回调
  private onPositionUpdate: ((position: number) => void) | null = null
  private onPlaybackStateChanged: ((isPlaying: boolean) => void) | null = null
  private onCompletion: (() => void) | null = null

  /**
   * 初始化播放器
   */
  async initialize(surfaceId: string): Promise<void> {
    this.surfaceId = surfaceId

    try {
      // 创建AVPlayer实例
      this.avPlayer = await media.createAVPlayer()

      // 设置事件监听
      this.setupEventListeners()

      // 设置Surface
      this.avPlayer.surfaceId = surfaceId

      hilog.info(DOMAIN, TAG, 'VideoPlayer initialized')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Initialize failed: ${JSON.stringify(error)}`)
      throw new Error(`VideoPlayer initialization failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 设置事件监听
   */
  private setupEventListeners(): void {
    if (!this.avPlayer) {
      return
    }

    // 状态变化
    this.avPlayer.on('stateChange', (state: string) => {
      hilog.debug(DOMAIN, TAG, `State change: ${state}`)

      if (state === 'playing') {
        this.isPlaying = true
      } else if (state === 'paused' || state === 'stopped') {
        this.isPlaying = false
      }

      this.onPlaybackStateChanged?.(this.isPlaying)
    })

    // 进度更新 - 使用timeUpdate事件
    this.avPlayer.on('timeUpdate', (time: number) => {
      this.currentPosition = time
      this.onPositionUpdate?.(time)
    })

    // 当duration可用时更新
    this.avPlayer.on('durationUpdate', (duration: number) => {
      this.duration = duration
      hilog.info(DOMAIN, TAG, `Duration updated: ${duration}ms`)
    })

    // 播放完成 - 监听stateChange，当状态变为stopped或completed时
    this.avPlayer.on('stateChange', (state: string) => {
      if (state === 'completed' || state === 'stopped') {
        hilog.info(DOMAIN, TAG, 'Playback completed')
        this.isPlaying = false
        this.onPlaybackStateChanged?.(false)
        this.onCompletion?.()
      }
    })

    // 错误处理
    this.avPlayer.on('error', (error: BusinessError) => {
      hilog.error(DOMAIN, TAG, `Player error: ${JSON.stringify(error)}`)
      hilog.error(DOMAIN, TAG, `Error code: ${error.code}, message: ${error.message}`)
    })
  }

  /**
   * 播放视频
   */
  async play(uri: string, httpHeaders: Record<string, string> = {}): Promise<void> {
    hilog.info(DOMAIN, TAG, `play方法被调用，avPlayer是否为null: ${this.avPlayer === null}`)
    hilog.info(DOMAIN, TAG, `HTTP头数量: ${Object.keys(httpHeaders).length}`)
    hilog.info(DOMAIN, TAG, `HTTP头内容: ${JSON.stringify(httpHeaders)}`)

    // 如果未初始化，尝试自动初始化（音频播放模式）
    if (!this.avPlayer) {
      hilog.warn(DOMAIN, TAG, 'VideoPlayer未初始化，尝试自动初始化...')
      try {
        // 创建AVPlayer实例
        this.avPlayer = await media.createAVPlayer()

        // 设置事件监听
        this.setupEventListeners()

        hilog.info(DOMAIN, TAG, 'VideoPlayer自动初始化成功（无Surface模式）')
      } catch (error) {
        hilog.error(DOMAIN, TAG, `VideoPlayer自动初始化失败: ${JSON.stringify(error)}`)
        throw new Error(`VideoPlayer initialization failed: ${JSON.stringify(error)}`)
      }
    }

    try {
      this.currentUri = uri

      // 设置HTTP头（如果支持）
      if (Object.keys(httpHeaders).length > 0) {
        hilog.info(DOMAIN, TAG, `设置HTTP头和媒体源`)

        // 使用鸿蒙的createMediaSourceWithUrl API设置HTTP头
        const mediaSource = media.createMediaSourceWithUrl(uri, httpHeaders)
        const playbackStrategy: media.PlaybackStrategy = {
          preferredWidth: 1920,
          preferredHeight: 1080,
          preferredBufferDuration: 5,  // 5秒缓冲（适应网络波动）
          preferredHdr: false
        }

        this.avPlayer.setMediaSource(mediaSource, playbackStrategy)
        hilog.info(DOMAIN, TAG, '媒体源已设置')
      } else {
        // 直接设置URL
        hilog.info(DOMAIN, TAG, '直接设置URL')
        this.avPlayer.url = uri
      }

      // 等待播放器准备好后再设置音量和播放
      // 先调用play()，播放器会自动准备
      await this.avPlayer.play()
      hilog.info(DOMAIN, TAG, 'play()已调用')

      // 现在设置音量（播放器已准备好）
      this.avPlayer.setVolume(1.0)
      hilog.info(DOMAIN, TAG, '设置音量: 1.0')

      hilog.info(DOMAIN, TAG, `Playing: ${uri.substring(0, 100)}...`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Play failed: ${JSON.stringify(error)}`)
      hilog.error(DOMAIN, TAG, `Error code: ${JSON.stringify((error as Record<string, Object>).code)}`)
      throw new Error(`VideoPlayer play failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 暂停播放
   */
  async pause(): Promise<void> {
    if (!this.avPlayer) {
      return
    }

    try {
      await this.avPlayer.pause()
      hilog.info(DOMAIN, TAG, 'Paused')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Pause failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 跳转到指定位置
   */
  async seek(position: number): Promise<void> {
    if (!this.avPlayer) {
      return
    }

    try {
      await this.avPlayer.seek(position)
      hilog.info(DOMAIN, TAG, `Seek to: ${position}ms`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Seek failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 停止播放
   */
  async stop(): Promise<void> {
    if (!this.avPlayer) {
      return
    }

    try {
      await this.avPlayer.stop()
      this.isPlaying = false
      this.currentPosition = 0
      hilog.info(DOMAIN, TAG, 'Stopped')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Stop failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 同步播放进度（从车机端同步）
   * 优化版本：动态阈值、平滑seek、防抖机制
   */
  syncProgress(position: number, duration: number, isPlaying: boolean): void {
    // 只有在手机播放模式下才同步进度
    if (!this.isPhoneMode) {
      hilog.debug(DOMAIN, TAG, '非手机播放模式，忽略进度同步')
      return
    }

    // 只有在视频真正开始播放后才允许同步
    // 如果currentPosition为0，说明视频还没开始播放，不要同步
    if (this.currentPosition === 0) {
      hilog.debug(DOMAIN, TAG, `视频未开始播放(currentPosition=0)，忽略进度同步`)
      return
    }

    // 清除之前的防抖定时器
    if (this.seekDebounceTimer !== -1) {
      clearTimeout(this.seekDebounceTimer)
    }

    // 设置防抖定时器
    this.seekDebounceTimer = setTimeout(() => {
      this.executeSync(position, duration, isPlaying)
    }, this.SEEK_DEBOUNCE_DELAY)
  }

  /**
   * 执行进度同步（内部方法，在防抖后调用）
   */
  private executeSync(position: number, duration: number, isPlaying: boolean): void {
    // 计算动态同步阈值
    const threshold = this.calculateSyncThreshold(duration)

    // 计算差异
    const difference = Math.abs(this.currentPosition - position)

    // 只有差异超过阈值才同步
    if (difference > threshold) {
      hilog.info(DOMAIN, TAG, `进度差异 ${difference}ms > ${threshold}ms，执行同步`)

      // 平滑同步：不直接跳转，而是使用平滑调整
      this.smoothSeek(position)
    } else {
      hilog.debug(DOMAIN, TAG, `进度差异 ${difference}ms <= ${threshold}ms，不同步`)
    }
  }

  /**
   * 根据视频长度计算同步阈值
   * 短视频（< 5分钟）：1秒阈值
   * 中等视频（5-30分钟）：2秒阈值
   * 长视频（> 30分钟）：3秒阈值
   */
  private calculateSyncThreshold(duration: number): number {
    if (duration <= 0) {
      return 1000 // 默认1秒
    }

    // 短视频（< 5分钟）：1秒阈值
    if (duration < 300000) {
      return 1000
    }

    // 中等视频（5-30分钟）：2秒阈值
    if (duration < 1800000) {
      return 2000
    }

    // 长视频（> 30分钟）：3秒阈值
    return 3000
  }

  /**
   * 平滑seek：避免卡顿
   * 小跳转（< 10秒）：直接跳转
   * 大跳转：分段跳转（避免缓冲打断）
   */
  private async smoothSeek(targetPosition: number): Promise<void> {
    const currentPosition = this.currentPosition
    const difference = targetPosition - currentPosition

    // 小跳转（< 10秒）：直接跳转
    if (Math.abs(difference) < 10000) {
      await this.seek(targetPosition)
      return
    }

    // 大跳转：分段跳转（避免缓冲打断）
    hilog.info(DOMAIN, TAG, `大跳转：${currentPosition} -> ${targetPosition}，分段执行`)
    const steps = 5
    const stepSize = difference / steps

    for (let i = 1; i <= steps; i++) {
      const intermediatePosition = currentPosition + (stepSize * i)
      hilog.debug(DOMAIN, TAG, `分段跳转 ${i}/${steps}: ${intermediatePosition}ms`)
      await this.seek(intermediatePosition)

      // 等待缓冲（100ms）
      await new Promise<void>(resolve => setTimeout(resolve, 100))
    }

    hilog.info(DOMAIN, TAG, '分段跳转完成')
  }

  /**
   * 设置手机播放模式
   * 用于标识当前是否在手机播放模式（启用进度同步）
   */
  setPhoneMode(enabled: boolean): void {
    this.isPhoneMode = enabled
    hilog.info(DOMAIN, TAG, `手机播放模式: ${enabled}`)
  }

  /**
   * 获取当前播放位置
   */
  getCurrentPosition(): number {
    return this.currentPosition
  }

  /**
   * 获取总时长
   */
  getDuration(): number {
    return this.duration
  }

  /**
   * 是否正在播放
   */
  getIsPlaying(): boolean {
    return this.isPlaying
  }

  /**
   * 释放资源
   */
  async release(): Promise<void> {
    if (this.avPlayer) {
      try {
        await this.avPlayer.release()
      } catch (error) {
        hilog.error(DOMAIN, TAG, `Release failed: ${JSON.stringify(error)}`)
      }
      this.avPlayer = null
    }

    this.isPlaying = false
    this.currentPosition = 0
    hilog.info(DOMAIN, TAG, 'Released')
  }
}
