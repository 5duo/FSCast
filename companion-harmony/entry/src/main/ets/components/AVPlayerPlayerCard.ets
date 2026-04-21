/**
 * AVPlayer视频播放器卡片组件（使用全局VideoPlayer）
 * 支持倍速播放和缓冲进度显示
 */
import media from '@ohos.multimedia.media'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors } from '../constants/AppColors'
import { VideoPlayer } from '../service/VideoPlayer'

// 内联尺寸常量
const SPACING_SM = 8
const SPACING_MD = 12
const SPACING_LG = 16
const RADIUS_MD = 8
const RADIUS_LG = 12

const TAG = 'AVPlayerPlayerCard'
const DOMAIN = 0x0000

// 倍速选项接口
interface SpeedOption {
  label: string
  speed: media.PlaybackSpeed
}

// 全局VideoPlayer实例（单例）
let globalVideoPlayer: VideoPlayer | null = null

@Component
export struct AVPlayerPlayerCard {
  // 视频URI和HTTP头
  @Prop dlnaUri: string = ''
  @Prop dlnaHttpHeaders: Record<string, string> = {}

  // 播放状态
  @Prop dlnaIsPlaying: boolean = false
  @Prop dlnaPosition: number = 0
  @Prop dlnaDuration: number = 0

  // XComponent相关
  private surfaceId: string = ''
  private xComponentController: XComponentController = new XComponentController()

  // 倍速和缓冲状态（从VideoPlayer同步）
  @State currentSpeed: media.PlaybackSpeed = media.PlaybackSpeed.SPEED_FORWARD_1_00_X
  @State currentBufferTime: number = 0
  @State isBuffering: boolean = false

  // 回调函数
  onPlayStateChange?: (isPlaying: boolean) => void
  onProgressUpdate?: (position: number, duration: number) => void
  onCompletion?: () => void
  onError?: (error: ESObject) => void
  onSpeedChange?: (speed: media.PlaybackSpeed) => void

  /**
   * 获取全局VideoPlayer实例
   */
  getVideoPlayer(): VideoPlayer {
    if (!globalVideoPlayer) {
      globalVideoPlayer = new VideoPlayer()
      hilog.info(DOMAIN, TAG, '创建全局VideoPlayer实例')
    }
    return globalVideoPlayer
  }

  /**
   * 同步状态
   */
  syncState(): void {
    const player = this.getVideoPlayer()
    this.currentSpeed = player.getCurrentSpeed()
    this.currentBufferTime = player.getCurrentBufferTime()
    this.isBuffering = player.getIsBuffering()
  }

  build() {
    Column() {
      // 标题和状态
      Row() {
        Text('视频播放')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(AppColors.TEXT_PRIMARY)

        Blank()

        // 播放状态指示
        Row({ space: 6 }) {
          Circle()
            .width(8)
            .height(8)
            .fill(this.dlnaIsPlaying ? AppColors.SUCCESS : AppColors.NEUTRAL)

          Text(this.dlnaIsPlaying ? '播放中' : '已暂停')
            .fontSize(12)
            .fontColor(this.dlnaIsPlaying ? AppColors.SUCCESS : AppColors.NEUTRAL)
        }

        // 缓冲状态指示
        if (this.isBuffering) {
          Row({ space: 6 }) {
            LoadingProgress()
              .width(16)
              .height(16)
              .color(AppColors.PRIMARY)

            Text('缓冲中...')
              .fontSize(12)
              .fontColor(AppColors.PRIMARY)
          }
          .margin({ left: 8 })
        }
      }
      .width('100%')
      .margin({ bottom: 12 })

      // 视频显示区域（XComponent）
      if (this.dlnaUri.length > 0) {
        XComponent({
          id: 'AVPlayerXComponent',
          type: XComponentType.SURFACE,
          controller: this.xComponentController
        })
          .width('100%')
          .aspectRatio(16 / 9)
          .backgroundColor('#000000')
          .borderRadius(8)
          .onLoad((event: Record<string, Object>) => {
            // event.data is directly the surfaceId string for XComponentType.SURFACE
            if (event && event.data) {
              this.surfaceId = event.data as string || ''
            }
            hilog.info(DOMAIN, TAG, `XComponent loaded, surfaceId: ${this.surfaceId}`)
            hilog.info(DOMAIN, TAG, `event.data type: ${typeof event.data}, value: ${JSON.stringify(event.data)}`)

            // 初始化VideoPlayer
            if (this.dlnaUri.length > 0 && this.surfaceId.length > 0) {
              const player = this.getVideoPlayer()
              player.initialize(this.surfaceId).then(() => {
                hilog.info(DOMAIN, TAG, 'VideoPlayer初始化成功')
                player.play(this.dlnaUri, this.dlnaHttpHeaders)
                // 设置回调
                player.onPositionUpdate = (position: number) => {
                  this.onProgressUpdate?.(position, player.getDuration())
                }
                player.onPlaybackStateChanged = (isPlaying: boolean) => {
                  this.onPlayStateChange?.(isPlaying)
                }
                player.onCompletion = () => {
                  this.onCompletion?.()
                }
              }).catch((error: Error) => {
                hilog.error(DOMAIN, TAG, `VideoPlayer初始化失败: ${error.message}`)
                this.onError?.({ message: error.message })
              })
            } else if (this.dlnaUri.length > 0 && this.surfaceId.length === 0) {
              hilog.error(DOMAIN, TAG, 'surfaceId为空，无法初始化VideoPlayer')
            }
          })
      } else {
        // 等待URL占位符
        Column() {
          Text('⏸')
            .fontSize(48)
            .fontColor(AppColors.TEXT_DISABLED)
            .margin({ bottom: 12 })

          Text('等待投屏...')
            .fontSize(14)
            .fontColor(AppColors.TEXT_SECONDARY)
        }
        .width('100%')
        .aspectRatio(16 / 9)
        .backgroundColor(AppColors.BG_PRIMARY)
        .borderRadius(8)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }

      // 缓冲进度条（底层）和播放进度条（上层）
      if (this.dlnaDuration > 0) {
        Stack({ alignContent: Alignment.Center }) {
          // 缓冲进度条（灰色）
          Progress({
            value: this.currentBufferTime,
            total: this.dlnaDuration,
            type: ProgressType.Linear
          })
            .width('100%')
            .color(Color.Gray)
            .backgroundColor(Color.Transparent)
            .height(4)

          // 播放进度条（蓝色）
          Progress({
            value: this.dlnaPosition,
            total: this.dlnaDuration,
            type: ProgressType.Linear
          })
            .width('100%')
            .color('#4A90E2')
            .backgroundColor(Color.Transparent)
            .height(4)
        }
        .width('100%')
        .margin({ top: 8 })
      }

      // 倍速控制按钮组
      Row({ space: 8 }) {
        ForEach([
          { label: '1.0x', speed: media.PlaybackSpeed.SPEED_FORWARD_1_00_X } as SpeedOption,
          { label: '1.25x', speed: media.PlaybackSpeed.SPEED_FORWARD_1_25_X } as SpeedOption,
          { label: '1.75x', speed: media.PlaybackSpeed.SPEED_FORWARD_1_75_X } as SpeedOption,
          { label: '2.0x', speed: media.PlaybackSpeed.SPEED_FORWARD_2_00_X } as SpeedOption
        ], (item: SpeedOption) => {
          Button(item.label)
            .fontSize(12)
            .backgroundColor(this.currentSpeed === item.speed ? '#4A90E2' : Color.Gray)
            .fontColor(Color.White)
            .height(28)
            .onClick(() => {
              this.getVideoPlayer().setPlaybackSpeed(item.speed)
              this.syncState()
            })
        })
      }
      .width('100%')
      .margin({ top: 8 })
      .justifyContent(FlexAlign.SpaceEvenly)

      // 时间显示
      if (this.dlnaDuration > 0) {
        Row() {
          Text(`${this.formatTime(this.dlnaPosition)} / ${this.formatTime(this.dlnaDuration)}`)
            .fontSize(12)
            .fontColor(AppColors.TEXT_SECONDARY)
        }
        .width('100%')
        .margin({ top: 4 })
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(12)
  }

  /**
   * 格式化时间显示
   */
  private formatTime(ms: number): string {
    const totalSeconds = Math.floor(ms / 1000)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}:${seconds.toString().padStart(2, '0')}`
  }

  /**
   * 组件销毁时释放资源
   */
  aboutToDisappear(): void {
    // 不释放全局VideoPlayer，保持单例
  }
}

/**
 * 导出全局VideoPlayer访问函数
 */
export function getGlobalVideoPlayer(): VideoPlayer | null {
  return globalVideoPlayer
}
