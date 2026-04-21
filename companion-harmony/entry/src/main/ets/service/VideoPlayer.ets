/**
 * 视频播放器
 * FSCast Remote - 使用AVPlayer播放HTTP视频流
 * 支持视频+音频播放和进度同步，支持后台播放
 */
import media from '@ohos.multimedia.media'
import audio from '@ohos.multimedia.audio'
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
  private readonly SEEK_DEBOUNCE_DELAY = 1000  // 1秒防抖延迟
  private hasStartedPlaying: boolean = false  // 新增：标志视频是否已经开始播放过

  // 倍速播放相关（基于官方文档）
  currentSpeed: media.PlaybackSpeed = media.PlaybackSpeed.SPEED_FORWARD_1_00_X
  private speedSyncPromise: Promise<void> | null = null

  // 缓冲进度相关（基于官方流媒体文档）
  currentBufferTime: number = 0  // 已缓冲时长（毫秒）
  private isBuffering: boolean = false

  // 平滑同步状态（基于官方倍速API）
  syncState: 'synced' | 'catching_up' | 'seeking' = 'synced'

  // 事件回调
  onPositionUpdate: ((position: number) => void) | null = null
  onPlaybackStateChanged: ((isPlaying: boolean) => void) | null = null
  onCompletion: (() => void) | null = null

  /**
   * 初始化播放器
   * 注意：不在这里设置surfaceId，而是在play()方法中设置
   */
  async initialize(surfaceId: string): Promise<void> {
    // 如果AVPlayer已经存在且正在播放，不重新初始化
    if (this.avPlayer && (this.isPlaying || this.currentPosition > 0)) {
      hilog.info(DOMAIN, TAG, 'VideoPlayer已经初始化且正在播放，更新SurfaceID')
      this.surfaceId = surfaceId
      return
    }

    // 如果AVPlayer存在但未播放，先释放再重新创建
    if (this.avPlayer) {
      hilog.info(DOMAIN, TAG, '释放旧的VideoPlayer实例')
      try {
        await this.avPlayer.release()
      } catch (error) {
        hilog.warn(DOMAIN, TAG, `释放旧VideoPlayer失败: ${JSON.stringify(error)}`)
      }
      this.avPlayer = null
    }

    this.surfaceId = surfaceId

    try {
      // 创建AVPlayer实例
      this.avPlayer = await media.createAVPlayer()

      // 设置事件监听（必须在设置资源之前）
      this.setupEventListeners()

      hilog.info(DOMAIN, TAG, 'VideoPlayer创建成功，等待播放时设置Surface')
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
        this.hasStartedPlaying = true  // 标记视频已开始播放
        hilog.info(DOMAIN, TAG, `视频开始播放，允许进度同步`)
      } else if (state === 'paused' || state === 'stopped') {
        this.isPlaying = false
      } else if (state === 'prepared') {
        // 🆕 关键修复：在prepared状态设置SurfaceID
        hilog.info(DOMAIN, TAG, `AVPlayer进入prepared状态，设置SurfaceID: ${this.surfaceId}`)
        if (this.surfaceId.length > 0 && this.avPlayer) {
          this.avPlayer.surfaceId = this.surfaceId
          hilog.info(DOMAIN, TAG, `SurfaceID已设置，现在可以播放`)
        }

        // 自动开始播放
        if (this.avPlayer) {
          this.avPlayer.play().then(() => {
            hilog.info(DOMAIN, TAG, '自动播放开始')
          }).catch((error: Error) => {
            hilog.error(DOMAIN, TAG, `自动播放失败: ${error.message}`)
          })
        }
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

    // 倍速设置完成监听（基于官方文档）
    this.avPlayer.on('speedDone', (speed: media.PlaybackSpeed) => {
      this.currentSpeed = speed
      hilog.info(DOMAIN, TAG, `倍速已切换: ${speed}`)
    })

    // 缓冲进度监听（基于官方流媒体文档）
    this.avPlayer.on('bufferingUpdate', (infoType: media.BufferingInfoType, value: number) => {
      hilog.debug(DOMAIN, TAG, `bufferingUpdate: ${infoType}, ${value}`)

      if (infoType === media.BufferingInfoType.CACHED_DURATION) {
        // 已缓冲时长（毫秒）
        this.currentBufferTime = this.currentPosition + value
      } else if (infoType === media.BufferingInfoType.BUFFERING_START) {
        this.isBuffering = true
        hilog.info(DOMAIN, TAG, '开始缓冲')
      } else if (infoType === media.BufferingInfoType.BUFFERING_END) {
        this.isBuffering = false
        hilog.info(DOMAIN, TAG, '缓冲结束')
      }
    })

    // seek完成监听（用于精确同步）
    this.avPlayer.on('seekDone', (seekDoneTime: number) => {
      hilog.info(DOMAIN, TAG, `seek完成: ${seekDoneTime}ms`)
      this.currentPosition = seekDoneTime
      // 触发UI更新回调
      this.onPositionUpdate?.(seekDoneTime)
    })

    // 音量变化监听
    this.avPlayer.on('volumeChange', (volume: number) => {
      hilog.debug(DOMAIN, TAG, `音量变化: ${volume}`)
    })

    // 音频焦点中断监听
    this.avPlayer.on('audioInterrupt', (interruptEvent: audio.InterruptEvent) => {
      hilog.info(DOMAIN, TAG, `音频中断: ${JSON.stringify(interruptEvent)}`)
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

      // ⚠️ 重要：不在此时设置SurfaceID
      // SurfaceID必须在AVPlayer进入initialized状态后才能设置
      // 会在stateChange监听中，当状态变为prepared时设置

      // 等待AVPlayer准备好后再播放
      hilog.info(DOMAIN, TAG, '等待AVPlayer准备...')

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
    // 使用hasStartedPlaying标志而不是currentPosition检查
    if (!this.hasStartedPlaying) {
      hilog.debug(DOMAIN, TAG, `视频尚未开始播放，忽略进度同步`)
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

  /**
   * 设置播放倍速（基于官方文档 bpta-avplayer-basic-control）
   * @param speed 播放速度枚举
   */
  async setPlaybackSpeed(speed: media.PlaybackSpeed): Promise<void> {
    if (!this.avPlayer) {
      hilog.warn(DOMAIN, TAG, 'AVPlayer未初始化，无法设置倍速')
      return
    }

    try {
      this.currentSpeed = speed
      await this.avPlayer.setSpeed(speed)
      hilog.info(DOMAIN, TAG, `倍速设置: ${speed}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `倍速设置失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 获取当前倍速
   */
  getCurrentSpeed(): media.PlaybackSpeed {
    return this.currentSpeed
  }

  /**
   * 获取当前缓冲时长
   */
  getCurrentBufferTime(): number {
    return this.currentBufferTime
  }

  /**
   * 是否正在缓冲
   */
  getIsBuffering(): boolean {
    return this.isBuffering
  }

  /**
   * 获取同步状态
   */
  getSyncState(): 'synced' | 'catching_up' | 'seeking' {
    return this.syncState
  }

  /**
   * 平滑同步方法（基于VideoTogether时间戳对齐算法）
   * 根据进度差异自动选择合适的同步策略
   * @param carPosition 车机端位置（毫秒）
   * @param carSendTime 车机端发送时间（毫秒，可选）
   */
  syncWithSmoothSpeed(carPosition: number, carSendTime?: number): void {
    // 如果未开始播放，不执行同步
    if (!this.hasStartedPlaying) {
      hilog.debug(DOMAIN, TAG, '视频尚未开始播放，跳过平滑同步')
      return
    }

    // 时间戳对齐：计算车机当前的真实位置
    let alignedCarPosition = carPosition
    if (carSendTime) {
      const currentTime = Date.now()
      const networkDelay = currentTime - carSendTime
      alignedCarPosition = carPosition + networkDelay
      hilog.debug(DOMAIN, TAG, `时间戳对齐: 车机发送=${carPosition}ms, 延迟=${networkDelay}ms, 对齐后=${alignedCarPosition}ms`)
    }

    const diff = alignedCarPosition - this.currentPosition  // 毫秒（正数=落后，负数=超前）
    const absDiff = Math.abs(diff)

    if (absDiff < 1000) {
      // 🎯 误差<1秒：已同步，正常播放
      this.setPlaybackSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_00_X)
      this.syncState = 'synced'
      // 每10秒输出一次状态
      if (Date.now() % 10000 < 3000) {
        hilog.debug(DOMAIN, TAG, `✅ 同步正常: 差异${Math.floor(diff)}ms`)
      }
    } else if (absDiff < 3000) {
      // 🎯 误差1-3秒：微调倍速追赶（1.25x）
      if (diff > 0) {
        this.setPlaybackSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_25_X)
        this.syncState = 'catching_up'
        hilog.info(DOMAIN, TAG, `🚀 微调追赶: 落后${Math.floor(diff)}ms, 使用1.25x`)
      } else {
        this.setPlaybackSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_00_X)
        this.syncState = 'synced'
        hilog.info(DOMAIN, TAG, `⏸️ 手机超前${Math.floor(Math.abs(diff))}ms, 等待车机`)
      }
    } else if (absDiff < 10000) {
      // 🎯 误差3-10秒：中等倍速追赶（1.75x）
      if (diff > 0) {
        this.setPlaybackSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_75_X)
        this.syncState = 'catching_up'
        hilog.info(DOMAIN, TAG, `🚀 中等追赶: 落后${Math.floor(diff)}ms, 使用1.75x`)
      } else {
        this.setPlaybackSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_00_X)
        this.syncState = 'synced'
      }
    } else if (absDiff < 20000) {
      // 🎯 误差10-20秒：高速追赶（2.0x）
      if (diff > 0) {
        this.setPlaybackSpeed(media.PlaybackSpeed.SPEED_FORWARD_2_00_X)
        this.syncState = 'catching_up'
        hilog.info(DOMAIN, TAG, `🚀 高速追赶: 落后${Math.floor(diff)}ms, 使用2.0x`)
      } else {
        this.setPlaybackSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_00_X)
        this.syncState = 'synced'
      }
    } else {
      // 🎯 误差>20秒：强制seek对齐（最后手段）
      this.syncState = 'seeking'
      const seekTarget = alignedCarPosition

      hilog.info(DOMAIN, TAG, `⚡ 强制对齐(最后手段): 差异${Math.floor(diff)}ms, seek到${Math.floor(seekTarget / 1000)}s`)

      this.seek(seekTarget).then(() => {
        hilog.info(DOMAIN, TAG, `✅ 强制对齐完成`)
      })
    }
  }
}
