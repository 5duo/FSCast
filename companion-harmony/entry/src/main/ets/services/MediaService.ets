/**
 * 媒体服务（整合版）
 * 整合AVSession后台播放和BackgroundTasks长时任务
 * 支持通知栏控制、锁屏控制、后台音频播放
 */
import { avSession as AVSessionManager } from '@kit.AVSessionKit'
import { backgroundTaskManager } from '@kit.BackgroundTasksKit'
import { wantAgent, WantAgent } from '@kit.AbilityKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { Logger } from '../utils/Logger'

const TAG = 'MediaService'
const logger = new Logger(TAG, 0x0000)

/**
 * 媒体元数据接口
 */
export interface MediaMetadata {
  assetId: string
  title: string
  artist: string
  duration: number
}

/**
 * 播放状态接口
 */
export interface MediaPlaybackState {
  isPlaying: boolean
  position: number
  duration: number
  speed: number
}

/**
 * 播放控制回调
 */
export interface MediaControlCallbacks {
  onPlay?: () => void
  onPause?: () => void
  onStop?: () => void
  onSeek?: (position: number) => void
}

export class MediaService {
  private context: Context | null = null
  private avSession: AVSessionManager.AVSession | null = null
  private isSessionActivated: boolean = false

  // 长时任务状态
  private isBackgroundRunning: boolean = false

  // 控制回调
  private callbacks: MediaControlCallbacks = {}

  /**
   * 设置上下文
   */
  setContext(context: Context): void {
    this.context = context
    logger.info('Context set')
  }

  /**
   * 设置播放控制回调
   */
  setCallbacks(callbacks: MediaControlCallbacks): void {
    this.callbacks = { ...this.callbacks, ...callbacks }
  }

  /**
   * 创建并激活AVSession
   */
  async createSession(): Promise<void> {
    if (!this.context) {
      logger.error('Context not set')
      throw new Error('Context not set')
    }

    try {
      logger.info('Creating AVSession...')

      // 创建AVSession
      this.avSession = await AVSessionManager.createAVSession(
        this.context,
        'FSCastAudioSession',
        'audio'
      )

      logger.info(`AVSession created: ${this.avSession.sessionId}`)

      // 注册控制命令监听
      this.registerControlCommands()

      // 激活会话
      await this.avSession.activate()
      this.isSessionActivated = true

      logger.info('AVSession activated')

    } catch (error) {
      logger.error(`Create AVSession failed: ${(error as BusinessError).message}`)
      throw error
    }
  }

  /**
   * 注册播放控制命令监听
   */
  private registerControlCommands(): void {
    if (!this.avSession) return

    // 播放
    this.avSession.on('play', () => {
      logger.info('Play command received from AVSession')
      this.callbacks.onPlay?.()
    })

    // 暂停
    this.avSession.on('pause', () => {
      logger.info('Pause command received from AVSession')
      this.callbacks.onPause?.()
    })

    // 停止
    this.avSession.on('stop', () => {
      logger.info('Stop command received from AVSession')
      this.callbacks.onStop?.()
    })

    // 跳转
    this.avSession.on('seek', (position: number) => {
      logger.info(`Seek command received from AVSession: ${position}ms`)
      this.callbacks.onSeek?.(position)
    })

    logger.debug('Control commands registered')
  }

  /**
   * 设置媒体元数据
   */
  async setMetadata(metadata: MediaMetadata): Promise<void> {
    if (!this.avSession) {
      logger.warn('AVSession not created, skipping setMetadata')
      return
    }

    try {
      const avMetadata: AVSessionManager.AVMetadata = {
        assetId: metadata.assetId,
        title: metadata.title,
        artist: metadata.artist,
        duration: metadata.duration
      }

      await this.avSession.setAVMetadata(avMetadata)
      logger.info(`Metadata set: ${metadata.title}`)

    } catch (error) {
      logger.error(`Set metadata failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 设置播放状态
   */
  async setPlaybackState(state: MediaPlaybackState): Promise<void> {
    if (!this.avSession) {
      logger.warn('AVSession not created, skipping setPlaybackState')
      return
    }

    try {
      const playbackState: AVSessionManager.AVPlaybackState = {
        state: state.isPlaying
          ? AVSessionManager.PlaybackState.PLAYBACK_STATE_PLAY
          : AVSessionManager.PlaybackState.PLAYBACK_STATE_PAUSE,
        position: {
          elapsedTime: state.position,
          updateTime: Date.now()
        },
        speed: state.speed,
        bufferedTime: 0
      }

      await this.avSession.setAVPlaybackState(playbackState)
      logger.debug(`Playback state set: ${state.isPlaying ? 'playing' : 'paused'}`)

    } catch (error) {
      logger.error(`Set playback state failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 启动后台长时任务
   */
  async startBackgroundService(): Promise<void> {
    if (!this.context) {
      logger.error('Context not set')
      return
    }

    if (this.isBackgroundRunning) {
      logger.debug('Background service already running')
      return
    }

    try {
      logger.info('Starting background service...')

      // 创建WantAgent
      const wantAgentInfo: wantAgent.WantAgentInfo = {
        wants: [{
          bundleName: 'com.example.fscastremote',
          abilityName: 'EntryAbility'
        }],
        actionType: wantAgent.OperationType.START_ABILITY,
        requestCode: 0,
        actionFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
      }

      const wantAgentObj: WantAgent = await wantAgent.getWantAgent(wantAgentInfo)

      // 申请长时任务
      await backgroundTaskManager.startBackgroundRunning(
        this.context,
        backgroundTaskManager.BackgroundMode.AUDIO_PLAYBACK,
        wantAgentObj
      )

      this.isBackgroundRunning = true
      logger.info('Background service started')

    } catch (error) {
      logger.error(`Start background service failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 停止后台长时任务
   */
  async stopBackgroundService(): Promise<void> {
    if (!this.context) {
      logger.error('Context not set')
      return
    }

    if (!this.isBackgroundRunning) {
      logger.debug('Background service not running')
      return
    }

    try {
      logger.info('Stopping background service...')

      await backgroundTaskManager.cancelBackgroundRunning(this.context)

      this.isBackgroundRunning = false
      logger.info('Background service stopped')

    } catch (error) {
      logger.error(`Stop background service failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 会话是否已激活
   */
  isActivated(): boolean {
    return this.isSessionActivated
  }

  /**
   * 后台服务是否运行中
   */
  isBackgroundServiceRunning(): boolean {
    return this.isBackgroundRunning
  }

  /**
   * 释放资源
   */
  async release(): Promise<void> {
    try {
      logger.info('Releasing MediaService...')

      // 停止后台服务
      await this.stopBackgroundService()

      // 停用会话
      if (this.avSession && this.isSessionActivated) {
        await this.avSession.deactivate()
        this.isSessionActivated = false
      }

      // 释放会话
      if (this.avSession) {
        await this.avSession.destroy()
        this.avSession = null
      }

      logger.info('MediaService released')

    } catch (error) {
      logger.error(`Release failed: ${JSON.stringify(error)}`)
    }
  }
}
