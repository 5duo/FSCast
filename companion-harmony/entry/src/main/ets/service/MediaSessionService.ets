/**
 * 媒体会话服务
 * 管理 AVSession，实现后台播放、通知栏控制、锁屏控制
 */
import { avSession as AVSessionManager } from '@kit.AVSessionKit'
import { notificationManager } from '@kit.NotificationKit'
import { backgroundTaskManager } from '@kit.BackgroundTasksKit'
import { wantAgent, WantAgent } from '@kit.AbilityKit'
import { audio } from '@kit.AudioKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'MediaSessionService'
const DOMAIN = 0x0000
const NOTIFICATION_ID = 1002

export class MediaSessionService {
  private context: Context | null = null
  private avSession: AVSessionManager.AVSession | null = null
  private isActivated: boolean = false
  private notificationId: number = NOTIFICATION_ID

  // 播放控制回调
  onPlay: (() => void) | null = null
  onPause: (() => void) | null = null
  onStop: (() => void) | null = null
  onPlayNext: (() => void) | null = null
  onPlayPrevious: (() => void) | null = null
  onSeek: ((position: number) => void) | null = null

  /**
   * 设置上下文
   */
  setContext(context: Context): void {
    this.context = context
  }

  /**
   * 创建并激活媒体会话
   */
  async createSession(): Promise<void> {
    if (!this.context) {
      hilog.error(DOMAIN, TAG, 'Context not set')
      return
    }

    try {
      hilog.info(DOMAIN, TAG, 'Creating AVSession...')

      // 创建 AVSession
      this.avSession = await AVSessionManager.createAVSession(
        this.context,
        'FSCastAudioSession',
        'audio'
      )

      hilog.info(DOMAIN, TAG, `AVSession created: ${this.avSession.sessionId}`)

      // 注册播放控制命令监听器
      this.registerControlCommands()

      // 激活会话
      await this.avSession.activate()
      this.isActivated = true
      hilog.info(DOMAIN, TAG, 'AVSession activated')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Create AVSession failed: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 注册播放控制命令监听器
   */
  private registerControlCommands(): void {
    if (!this.avSession) return

    // 播放
    this.avSession.on('play', () => {
      hilog.info(DOMAIN, TAG, 'Play command received')
      this.onPlay?.()
    })

    // 暂停
    this.avSession.on('pause', () => {
      hilog.info(DOMAIN, TAG, 'Pause command received')
      this.onPause?.()
    })

    // 停止
    this.avSession.on('stop', () => {
      hilog.info(DOMAIN, TAG, 'Stop command received')
      this.onStop?.()
    })

    // 下一首
    this.avSession.on('playNext', () => {
      hilog.info(DOMAIN, TAG, 'Play next command received')
      this.onPlayNext?.()
    })

    // 上一首
    this.avSession.on('playPrevious', () => {
      hilog.info(DOMAIN, TAG, 'Play previous command received')
      this.onPlayPrevious?.()
    })

    // 快进/快退
    this.avSession.on('skipToQueueItem', (itemId: number) => {
      hilog.info(DOMAIN, TAG, `Skip to item: ${itemId}`)
    })

    // 拖动进度
    this.avSession.on('seek', (position: number) => {
      hilog.info(DOMAIN, TAG, `Seek to: ${position}`)
      this.onSeek?.(position)
    })

    // 循环模式
    this.avSession.on('setLoopMode', (loopMode: AVSessionManager.LoopMode) => {
      hilog.info(DOMAIN, TAG, `Loop mode: ${loopMode}`)
    })

    // 自定义数据
    this.avSession.on('customDataChange', (data: ESObject) => {
      hilog.info(DOMAIN, TAG, `Custom data: ${JSON.stringify(data)}`)
    })

    hilog.info(DOMAIN, TAG, 'Control commands registered')
  }

  /**
   * 设置媒体元数据
   */
  async setMetadata(title: string, artist: string = '', duration: number = 0): Promise<void> {
    if (!this.avSession) {
      hilog.warn(DOMAIN, TAG, 'AVSession not created')
      return
    }

    try {
      const metadata: AVSessionManager.AVMetadata = {
        assetId: 'FSCast-' + Date.now(),
        title: title,
        artist: artist,
        album: 'FSCast Remote',
        duration: duration,
        mediaImage: '' // 可以设置封面图片URL
      }

      await this.avSession.setAVMetadata(metadata)
      hilog.info(DOMAIN, TAG, `Metadata set: ${title}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Set metadata failed: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 设置播放状态
   */
  async setPlaybackState(
    isPlaying: boolean,
    position: number = 0,
    duration: number = 0,
    speed: number = 1.0
  ): Promise<void> {
    if (!this.avSession) {
      hilog.warn(DOMAIN, TAG, 'AVSession not created')
      return
    }

    try {
      const playbackState: AVSessionManager.AVPlaybackState = {
        state: isPlaying ? AVSessionManager.PlaybackState.PLAYBACK_STATE_PLAY : AVSessionManager.PlaybackState.PLAYBACK_STATE_PAUSE,
        speed: speed,
        position: {
          elapsedTime: position,
          updateTime: Date.now()
        },
        bufferedTime: 0,
        loopMode: AVSessionManager.LoopMode.LOOP_MODE_SEQUENCE,
        isFavorite: false
      }

      await this.avSession.setAVPlaybackState(playbackState)
      hilog.debug(DOMAIN, TAG, `Playback state set: ${isPlaying ? 'playing' : 'paused'} at ${position}ms`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Set playback state failed: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 启动后台服务
   * 注意：AVSession 会自动创建媒体控制通知，不需要手动发布通知
   */
  async startBackgroundService(): Promise<void> {
    if (!this.context) return

    try {
      // 申请持续后台任务（不创建通知，让 AVSession 自动管理）
      let wantAgentInfo: wantAgent.WantAgentInfo = {
        wants: [
          {
            bundleName: 'com.example.fscastremote',
            abilityName: 'EntryAbility'
          }
        ],
        actionType: wantAgent.OperationType.START_ABILITY,
        requestCode: 0
      }

      const wantAgentObj: WantAgent = await wantAgent.getWantAgent(wantAgentInfo)

      await backgroundTaskManager.startBackgroundRunning(
        this.context,
        backgroundTaskManager.BackgroundMode.AUDIO_PLAYBACK,
        wantAgentObj
      )

      hilog.info(DOMAIN, TAG, 'Background task started (AVSession will handle notification)')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Start background task failed: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 停止后台服务
   */
  async stopBackgroundService(): Promise<void> {
    if (!this.context) return

    try {
      await backgroundTaskManager.stopBackgroundRunning(this.context)
      // 取消通知（如果 AVSession 创建了通知）
      await notificationManager.cancelAll()
      hilog.info(DOMAIN, TAG, 'Background service stopped')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Stop background service failed: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 释放资源
   */
  async release(): Promise<void> {
    if (this.avSession && this.isActivated) {
      try {
        await this.avSession.deactivate()
        this.isActivated = false
        hilog.info(DOMAIN, TAG, 'AVSession deactivated')
      } catch (error) {
        hilog.error(DOMAIN, TAG, `Deactivate failed: ${(error as BusinessError).message}`)
      }
    }

    await this.stopBackgroundService()
    this.avSession = null
  }

  /**
   * 获取会话ID
   */
  getSessionId(): string {
    return this.avSession?.sessionId || ''
  }

  /**
   * 检查是否已激活
   */
  isActive(): boolean {
    return this.isActivated
  }
}
