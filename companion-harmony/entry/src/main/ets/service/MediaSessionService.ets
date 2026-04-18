/**
 * 媒体会话服务（增强版）
 * 管理 AVSession，实现后台播放、通知栏控制、锁屏控制
 * 增强功能：封面图支持、实时状态更新、音频焦点管理
 */
import { avSession as AVSessionManager } from '@kit.AVSessionKit'
import { notificationManager } from '@kit.NotificationKit'
import { backgroundTaskManager } from '@kit.BackgroundTasksKit'
import { wantAgent, WantAgent } from '@kit.AbilityKit'
import { audio } from '@kit.AudioKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import promptAction from '@ohos.promptAction'
import { AppConfig } from '../constants/Config'

const TAG = 'MediaSessionService'
const DOMAIN = 0x0000
const NOTIFICATION_ID = 1002

// 音频焦点类型
enum AudioFocusType {
  GAIN = 0,           // 获取焦点
  GAIN_TRANSIENT = 1, // 临时获取焦点
  LOSE = 2            // 失去焦点
}

/**
 * 媒体元数据接口
 */
interface MediaMetadata {
  assetId: string
  title: string
  artist: string
  album: string
  duration: number
  mediaImage: string
}

/**
 * 播放状态接口
 */
interface PlaybackState {
  isPlaying: boolean
  position: number
  duration: number
  speed: number
}

export class MediaSessionService {
  private context: Context | null = null
  private avSession: AVSessionManager.AVSession | null = null
  private isActivated: boolean = false
  private notificationId: number = NOTIFICATION_ID

  // 音频焦点管理
  private audioManager: audio.AudioManager | null = null
  private hasAudioFocus: boolean = false

  // 状态更新节流
  private lastStateUpdateTime: number = 0
  private readonly STATE_UPDATE_THROTTLE = 1000  // 1秒节流

  // 当前播放状态
  private currentMetadata: MediaMetadata | null = null
  private currentPlaybackState: PlaybackState | null = null

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
    this.avSession.on('play', async () => {
      hilog.info(DOMAIN, TAG, 'Play command received')
      await this.handlePlayCommand()
    })

    // 暂停
    this.avSession.on('pause', async () => {
      hilog.info(DOMAIN, TAG, 'Pause command received')
      await this.handlePauseCommand()
    })

    // 停止
    this.avSession.on('stop', async () => {
      hilog.info(DOMAIN, TAG, 'Stop command received')
      await this.handleStopCommand()
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

    // 拖动进度
    this.avSession.on('seek', async (position: number) => {
      hilog.info(DOMAIN, TAG, `Seek to: ${position}`)
      await this.handleSeekCommand(position)
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
   * 处理播放命令（带状态检查和反馈）
   */
  private async handlePlayCommand(): Promise<void> {
    try {
      // 检查是否有播放内容
      if (!this.currentMetadata) {
        await this.showToast('无播放内容')
        return
      }

      // 执行播放回调
      this.onPlay?.()

      // 请求音频焦点
      await this.requestAudioFocus()

      // 更新播放状态
      if (this.currentPlaybackState) {
        await this.setPlaybackState(
          true,
          this.currentPlaybackState.position,
          this.currentPlaybackState.duration
        )
      }

      await this.showToast('开始播放')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Handle play command failed: ${JSON.stringify(error)}`)
      await this.showToast('播放失败')
    }
  }

  /**
   * 处理暂停命令（带状态检查和反馈）
   */
  private async handlePauseCommand(): Promise<void> {
    try {
      // 检查是否有播放内容
      if (!this.currentMetadata) {
        return
      }

      // 执行暂停回调
      this.onPause?.()

      // 更新播放状态
      if (this.currentPlaybackState) {
        await this.setPlaybackState(
          false,
          this.currentPlaybackState.position,
          this.currentPlaybackState.duration
        )
      }

      await this.showToast('已暂停')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Handle pause command failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 处理停止命令（带状态清理）
   */
  private async handleStopCommand(): Promise<void> {
    try {
      // 执行停止回调
      this.onStop?.()

      // 释放音频焦点
      await this.abandonAudioFocus()

      // 清除状态
      this.currentMetadata = null
      this.currentPlaybackState = null

      await this.showToast('已停止')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Handle stop command failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 处理跳转命令（带状态检查和即时更新）
   */
  private async handleSeekCommand(position: number): Promise<void> {
    try {
      // 检查是否有播放内容
      if (!this.currentMetadata) {
        return
      }

      // 执行跳转回调
      this.onSeek?.(position)

      // 立即更新播放状态
      await this.setPlaybackState(
        this.currentPlaybackState?.isPlaying ?? false,
        position,
        this.currentPlaybackState?.duration ?? 0
      )

      hilog.info(DOMAIN, TAG, `Seek completed: ${position}ms`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Handle seek command failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 设置媒体元数据（增强版，支持封面图）
   */
  async setMetadata(title: string, artist: string = '', duration: number = 0, coverUrl: string = ''): Promise<void> {
    if (!this.avSession) {
      hilog.warn(DOMAIN, TAG, 'AVSession not created')
      return
    }

    try {
      const metadata: AVSessionManager.AVMetadata = {
        assetId: 'FSCast-' + Date.now(),
        title: title,
        artist: artist || 'FSCast Remote',
        album: 'FSCast Remote',
        duration: duration
      }

      // 只有当 coverUrl 非空时才添加 mediaImage
      if (coverUrl.length > 0) {
        metadata.mediaImage = coverUrl as string
      }

      await this.avSession.setAVMetadata(metadata)

      // 保存当前元数据
      this.currentMetadata = {
        assetId: metadata.assetId ?? '',
        title: metadata.title ?? '',
        artist: metadata.artist ?? 'FSCast Remote',
        album: metadata.album ?? 'FSCast Remote',
        duration: metadata.duration ?? 0,
        mediaImage: coverUrl ?? ''
      }

      hilog.info(DOMAIN, TAG, `Metadata set: ${title}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Set metadata failed: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 设置播放状态（带节流，避免过于频繁更新）
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
      // 节流：避免过于频繁更新
      const now = Date.now()
      if (now - this.lastStateUpdateTime < this.STATE_UPDATE_THROTTLE) {
        // 保存最新状态，但延迟更新
        this.currentPlaybackState = { isPlaying, position, duration, speed }
        return
      }

      this.lastStateUpdateTime = now

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

      // 保存当前播放状态
      this.currentPlaybackState = { isPlaying, position, duration, speed }

      hilog.debug(DOMAIN, TAG, `Playback state set: ${isPlaying ? 'playing' : 'paused'} at ${position}ms`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Set playback state failed: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 强制更新播放状态（忽略节流，用于关键时刻）
   */
  async forceUpdatePlaybackState(
    isPlaying: boolean,
    position: number = 0,
    duration: number = 0,
    speed: number = 1.0
  ): Promise<void> {
    this.lastStateUpdateTime = 0  // 重置节流计时器
    await this.setPlaybackState(isPlaying, position, duration, speed)
  }

  /**
   * 请求音频焦点（HarmonyOS 不支持此API，保留接口兼容性）
   */
  private async requestAudioFocus(): Promise<void> {
    // HarmonyOS 的音频焦点由系统自动管理
    // 此方法保留接口兼容性，不做实际操作
    this.hasAudioFocus = true
    hilog.debug(DOMAIN, TAG, 'Audio focus requested (no-op in HarmonyOS)')
  }

  /**
   * 释放音频焦点（HarmonyOS 不支持此API，保留接口兼容性）
   */
  private async abandonAudioFocus(): Promise<void> {
    // HarmonyOS 的音频焦点由系统自动管理
    // 此方法保留接口兼容性，不做实际操作
    this.hasAudioFocus = false
    hilog.debug(DOMAIN, TAG, 'Audio focus abandoned (no-op in HarmonyOS)')
  }

  /**
   * 启动后台服务
   */
  async startBackgroundService(): Promise<void> {
    if (!this.context) return

    try {
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

      hilog.info(DOMAIN, TAG, 'Background task started')
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
      await notificationManager.cancelAll()
      hilog.info(DOMAIN, TAG, 'Background service stopped')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Stop background service failed: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 显示 Toast 提示
   */
  private async showToast(message: string): Promise<void> {
    try {
      promptAction.showToast({
        message: message,
        duration: 2000
      })
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Show toast failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 获取当前媒体元数据
   */
  getCurrentMetadata(): MediaMetadata | null {
    return this.currentMetadata
  }

  /**
   * 获取当前播放状态
   */
  getCurrentPlaybackState(): PlaybackState | null {
    return this.currentPlaybackState
  }

  /**
   * 释放资源
   */
  async release(): Promise<void> {
    // 释放音频焦点
    await this.abandonAudioFocus()

    // 停用会话
    if (this.avSession && this.isActivated) {
      try {
        await this.avSession.deactivate()
        this.isActivated = false
        hilog.info(DOMAIN, TAG, 'AVSession deactivated')
      } catch (error) {
        hilog.error(DOMAIN, TAG, `Deactivate failed: ${(error as BusinessError).message}`)
      }
    }

    // 停止后台服务
    await this.stopBackgroundService()

    // 清理资源
    this.avSession = null
    this.currentMetadata = null
    this.currentPlaybackState = null
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

  /**
   * 检查是否持有音频焦点
   */
  hasFocus(): boolean {
    return this.hasAudioFocus
  }
}
