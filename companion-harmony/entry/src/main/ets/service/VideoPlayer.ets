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
   */
  syncProgress(position: number, duration: number, isPlaying: boolean): void {
    // 只有在视频真正开始播放后才允许同步
    // 如果currentPosition为0，说明视频还没开始播放，不要同步
    if (this.currentPosition === 0) {
      hilog.debug(DOMAIN, TAG, `视频未开始播放(currentPosition=0)，忽略进度同步`)
      return
    }

    // 如果差异超过1秒，进行同步
    if (Math.abs(this.currentPosition - position) > 1000) {
      hilog.debug(DOMAIN, TAG, `Syncing progress: ${this.currentPosition} -> ${position}`)
      this.seek(position)
    }
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
