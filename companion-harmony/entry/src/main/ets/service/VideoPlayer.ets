/**
 * 视频播放器
 * 鸿蒙手机端DLNA DMR - 使用AVPlayer播放HTTP视频流
 * 支持视频+音频播放和进度同步
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

  // 播放模式
  private playbackMode: 'video_audio' | 'audio_only' = 'video_audio'

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
    })
  }

  /**
   * 播放视频
   */
  async play(uri: string): Promise<void> {
    if (!this.avPlayer) {
      hilog.warn(DOMAIN, TAG, 'Not initialized')
      return
    }

    try {
      this.currentUri = uri

      // 设置视频URL
      this.avPlayer.url = uri

      // 开始播放
      await this.avPlayer.play()

      hilog.info(DOMAIN, TAG, `Playing: ${uri.substring(0, 100)}...`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Play failed: ${JSON.stringify(error)}`)
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
   * 设置播放模式
   */
  setPlaybackMode(mode: 'video_audio' | 'audio_only'): void {
    this.playbackMode = mode
    hilog.info(DOMAIN, TAG, `Playback mode: ${mode}`)
  }

  /**
   * 同步播放进度（从车机端同步）
   */
  syncProgress(position: number, duration: number, isPlaying: boolean): void {
    // 如果差异超过100ms，进行同步
    if (Math.abs(this.currentPosition - position) > 100) {
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
