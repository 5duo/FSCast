/**
 * 视频播放器服务（整合版）
 * 支持Video组件和AVPlayer两种模式
 * Video组件：简单场景，自动处理UI和控制
 * AVPlayer：高级场景，需要手动管理但更灵活
 */
import media from '@ohos.multimedia.media'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { Logger } from '../utils/Logger'

const TAG = 'VideoPlayerService'
const DOMAIN = 0x0000
const logger = new Logger(TAG, DOMAIN)

/**
 * 播放器模式
 */
export enum PlayerMode {
  VIDEO,      // Video组件模式（简单）
  AVPLAYER    // AVPlayer模式（高级）
}

/**
 * 播放状态
 */
export enum PlaybackState {
  IDLE = 'idle',
  PREPARING = 'preparing',
  PREPARED = 'prepared',
  PLAYING = 'playing',
  PAUSED = 'paused',
  STOPPED = 'stopped',
  COMPLETED = 'completed',
  ERROR = 'error'
}

/**
 * 播放器状态接口
 */
export interface PlayerState {
  mode: PlayerMode
  uri: string
  httpHeaders: Record<string, string>
  position: number
  duration: number
  isPlaying: boolean
  isPrepared: boolean
  playbackState: PlaybackState
}

/**
 * 播放器配置
 */
export interface PlayerConfig {
  mode: PlayerMode
  autoPlay: boolean
  preferredWidth: number
  preferredHeight: number
  preferredBufferDuration: number
}

/**
 * 播放控制回调
 */
export interface PlayerCallbacks {
  onPlayStateChange?: (isPlaying: boolean) => void
  onProgressUpdate?: (position: number, duration: number) => void
  onPrepared?: () => void
  onCompletion?: () => void
  onError?: (error: Error) => void
}

export class VideoPlayerService {
  private avPlayer: media.AVPlayer | null = null
  private state: PlayerState
  private callbacks: PlayerCallbacks

  constructor() {
    this.state = {
      mode: PlayerMode.VIDEO,
      uri: '',
      httpHeaders: {},
      position: 0,
      duration: 0,
      isPlaying: false,
      isPrepared: false,
      playbackState: PlaybackState.IDLE
    }
    this.callbacks = {}
    logger.info('VideoPlayerService initialized')
  }

  /**
   * 初始化AVPlayer（仅AVPlayer模式需要）
   */
  async initializeAVPlayer(): Promise<void> {
    try {
      logger.info('Initializing AVPlayer...')
      this.avPlayer = await media.createAVPlayer()
      this.setupAVPlayerEventListeners()
      logger.info('AVPlayer initialized successfully')
    } catch (error) {
      logger.error(`Failed to initialize AVPlayer: ${JSON.stringify(error)}`)
      throw new Error(`Failed to initialize AVPlayer: ${(error as Error).message}`)
    }
  }

  /**
   * 设置AVPlayer事件监听
   */
  private setupAVPlayerEventListeners(): void {
    if (!this.avPlayer) return

    // 状态变化
    this.avPlayer.on('stateChange', (state: string) => {
      logger.debug(`AVPlayer state change: ${state}`)

      switch (state) {
        case 'playing':
          this.state.isPlaying = true
          this.state.playbackState = PlaybackState.PLAYING
          this.callbacks.onPlayStateChange?.(true)
          break
        case 'paused':
          this.state.isPlaying = false
          this.state.playbackState = PlaybackState.PAUSED
          this.callbacks.onPlayStateChange?.(false)
          break
        case 'prepared':
          this.state.isPrepared = true
          this.state.playbackState = PlaybackState.PREPARED
          this.callbacks.onPrepared?.()
          break
        case 'completed':
          this.state.isPlaying = false
          this.state.playbackState = PlaybackState.COMPLETED
          this.callbacks.onPlayStateChange?.(false)
          this.callbacks.onCompletion?.()
          break
        case 'stopped':
          this.state.isPlaying = false
          this.state.playbackState = PlaybackState.STOPPED
          this.state.position = 0
          break
      }
    })

    // 进度更新
    this.avPlayer.on('timeUpdate', (time: number) => {
      this.state.position = time
      this.callbacks.onProgressUpdate?.(time, this.state.duration)
    })

    // 时长更新
    this.avPlayer.on('durationUpdate', (duration: number) => {
      this.state.duration = duration
      logger.debug(`Duration updated: ${duration}ms`)
    })

    // 错误处理
    this.avPlayer.on('error', (error: BusinessError) => {
      logger.error(`AVPlayer error: ${error.code} - ${error.message}`)
      this.state.playbackState = PlaybackState.ERROR
      this.callbacks.onError?.(new Error(error.message))
    })
  }

  /**
   * 使用Video组件播放（简单场景）
   * 返回URI和HTTP头，由Video组件处理
   */
  getVideoPlayerInfo(uri: string, httpHeaders: Record<string, string> = {}): {
    uri: string
    httpHeaders: Record<string, string>
  } {
    this.state.uri = uri
    this.state.httpHeaders = httpHeaders
    this.state.playbackState = PlaybackState.PREPARING

    logger.info(`Video component mode: URI set (${uri.length} chars)`)

    return {
      uri: uri,
      httpHeaders: httpHeaders
    }
  }

  /**
   * 使用AVPlayer播放（高级场景）
   */
  async playWithAVPlayer(
    uri: string,
    httpHeaders: Record<string, string> = {},
    config: Partial<PlayerConfig> = {}
  ): Promise<void> {
    try {
      logger.info(`AVPlayer mode: Starting playback`)
      logger.info(`URI: ${uri.substring(0, 100)}...`)
      logger.info(`HTTP headers: ${Object.keys(httpHeaders).length}`)

      // 确保AVPlayer已初始化
      if (!this.avPlayer) {
        await this.initializeAVPlayer()
      }

      this.state.uri = uri
      this.state.httpHeaders = httpHeaders
      this.state.playbackState = PlaybackState.PREPARING

      // 设置媒体源（支持HTTP头）
      if (Object.keys(httpHeaders).length > 0) {
        logger.info('Setting media source with HTTP headers')
        const mediaSource = media.createMediaSourceWithUrl(uri, httpHeaders)
        const playbackStrategy: media.PlaybackStrategy = {
          preferredWidth: config.preferredWidth || 1920,
          preferredHeight: config.preferredHeight || 1080,
          preferredBufferDuration: config.preferredBufferDuration || 5,
          preferredHdr: false
        }
        this.avPlayer.setMediaSource(mediaSource, playbackStrategy)
      } else {
        logger.info('Setting media URL directly')
        this.avPlayer.url = uri
      }

      // 开始播放
      await this.avPlayer.play()
      logger.info('AVPlayer playback started')

    } catch (error) {
      logger.error(`AVPlayer play failed: ${JSON.stringify(error)}`)
      this.state.playbackState = PlaybackState.ERROR
      throw new Error(`Failed to play: ${(error as Error).message}`)
    }
  }

  /**
   * 暂停播放
   */
  async pause(): Promise<void> {
    try {
      // AVPlayer模式
      if (this.avPlayer && this.state.mode === PlayerMode.AVPLAYER) {
        await this.avPlayer.pause()
        logger.info('AVPlayer paused')
      }

      // Video组件模式由Video组件自己处理
      // 这里只更新状态
      this.state.isPlaying = false
      logger.info('Pause called')
    } catch (error) {
      logger.error(`Pause failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 恢复播放
   */
  async resume(): Promise<void> {
    try {
      // AVPlayer模式
      if (this.avPlayer && this.state.mode === PlayerMode.AVPLAYER) {
        await this.avPlayer.play()
        logger.info('AVPlayer resumed')
      }

      // Video组件模式由Video组件自己处理
      this.state.isPlaying = true
      logger.info('Resume called')
    } catch (error) {
      logger.error(`Resume failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 跳转到指定位置
   */
  async seek(position: number): Promise<void> {
    try {
      logger.info(`Seek to ${position}ms`)

      // AVPlayer模式
      if (this.avPlayer && this.state.mode === PlayerMode.AVPLAYER) {
        await this.avPlayer.seek(position)
      }

      // Video组件模式由Video组件自己处理
      this.state.position = position
      logger.info('Seek completed')
    } catch (error) {
      logger.error(`Seek failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 停止播放
   */
  async stop(): Promise<void> {
    try {
      // AVPlayer模式
      if (this.avPlayer && this.state.mode === PlayerMode.AVPLAYER) {
        await this.avPlayer.stop()
        logger.info('AVPlayer stopped')
      }

      this.state.isPlaying = false
      this.state.position = 0
      this.state.playbackState = PlaybackState.STOPPED
      logger.info('Stop called')
    } catch (error) {
      logger.error(`Stop failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 设置播放器模式
   */
  setMode(mode: PlayerMode): void {
    logger.info(`Player mode changed: ${this.state.mode} -> ${mode}`)
    this.state.mode = mode
  }

  /**
   * 获取当前状态
   */
  getState(): PlayerState {
    return { ...this.state }
  }

  /**
   * 获取当前播放位置
   */
  getCurrentPosition(): number {
    return this.state.position
  }

  /**
   * 获取总时长
   */
  getDuration(): number {
    return this.state.duration
  }

  /**
   * 是否正在播放
   */
  isPlaying(): boolean {
    return this.state.isPlaying
  }

  /**
   * 是否已准备完成
   */
  isPrepared(): boolean {
    return this.state.isPrepared
  }

  /**
   * 设置回调
   */
  setCallbacks(callbacks: PlayerCallbacks): void {
    this.callbacks = { ...this.callbacks, ...callbacks }
  }

  /**
   * 释放资源
   */
  async release(): Promise<void> {
    try {
      if (this.avPlayer) {
        await this.avPlayer.release()
        this.avPlayer = null
      }

      this.state = {
        mode: PlayerMode.VIDEO,
        uri: '',
        httpHeaders: {},
        position: 0,
        duration: 0,
        isPlaying: false,
        isPrepared: false,
        playbackState: PlaybackState.IDLE
      }

      logger.info('Resources released')
    } catch (error) {
      logger.error(`Release failed: ${JSON.stringify(error)}`)
    }
  }
}
