/**
 * 播放器卡片组件（现代简洁风格）
 * 使用Video组件，提供简洁的UI和控制
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors } from '../constants/AppColors'

const TAG = 'PlayerCard'
const DOMAIN = 0x0000

/**
 * PlayerCard控制器
 */
export class PlayerCardController {
  cardInstance?: PlayerCard

  play() {
    this.cardInstance?.play()
  }

  pause() {
    this.cardInstance?.pause()
  }

  seek(positionMs: number) {
    this.cardInstance?.seek(positionMs)
  }

  getCurrentPosition(): number {
    return this.cardInstance?.getCurrentPosition() ?? 0
  }
}

@Component
export struct PlayerCard {
  // 控制器引用
  controller?: PlayerCardController

  // 视频属性
  @Prop uri: string = ''
  @Prop httpHeaders: Record<string, string> = {}
  @Prop isPlaying: boolean = false

  // 播放状态
  @State private currentPosition: number = 0
  @State private duration: number = 0

  // Video控制器
  private videoController: VideoController = new VideoController()

  // 回调
  onPlayStateChange?: (isPlaying: boolean) => void
  onProgressUpdate?: (position: number, duration: number) => void
  onCompletion?: () => void
  onError?: (error: ESObject) => void
  onPrepared?: () => void

  aboutToAppear() {
    hilog.info(DOMAIN, TAG, 'PlayerCard aboutToAppear')
  }

  /**
   * 播放
   */
  async play() {
    try {
      this.videoController.start()
      hilog.info(DOMAIN, TAG, 'Play called')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Play failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 暂停
   */
  async pause() {
    try {
      this.videoController.pause()
      hilog.info(DOMAIN, TAG, 'Pause called')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Pause failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 跳转
   */
  async seek(positionMs: number) {
    try {
      const positionSeconds = positionMs / 1000
      this.videoController.setCurrentTime(positionSeconds)
      hilog.info(DOMAIN, TAG, `Seek to ${positionSeconds}s`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Seek failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 获取当前位置
   */
  getCurrentPosition(): number {
    return this.currentPosition
  }

  build() {
    Column({ space: 12 }) {
      // 标题栏
      Row() {
        Text('视频播放')
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor(AppColors.TEXT_PRIMARY)

        Blank()

        // 播放状态指示
        if (this.isPlaying) {
          Row({ space: 6 }) {
            Circle()
              .width(8)
              .height(8)
              .fill(AppColors.STATUS_SUCCESS)

            Text('播放中')
              .fontSize(12)
              .fontColor(AppColors.STATUS_SUCCESS)
          }
        } else {
          Row({ space: 6 }) {
            Circle()
              .width(8)
              .height(8)
              .fill(AppColors.TEXT_DISABLED)

            Text('已暂停')
              .fontSize(12)
              .fontColor(AppColors.TEXT_TERTIARY)
          }
        }
      }
      .width('100%')

      // Video显示区域
      if (this.uri.length > 0) {
        Video({
          src: this.uri,
          controller: this.videoController
        })
          .width('100%')
          .aspectRatio(16 / 9)
          .backgroundColor('#000000')
          .borderRadius(12)
          .autoPlay(false)
          .controls(true)
          .loop(false)
          .objectFit(ImageFit.Contain)
          .onStart(() => {
            hilog.info(DOMAIN, TAG, 'Video started')
            this.onPlayStateChange?.(true)
          })
          .onPause(() => {
            hilog.info(DOMAIN, TAG, 'Video paused')
            this.onPlayStateChange?.(false)
          })
          .onFinish(() => {
            hilog.info(DOMAIN, TAG, 'Video finished')
            this.onPlayStateChange?.(false)
            this.onCompletion?.()
          })
          .onError((error: ESObject) => {
            hilog.error(DOMAIN, TAG, `Video error: ${JSON.stringify(error)}`)
            this.onError?.(error)
          })
          .onPrepared((info: PreparedInfo) => {
            hilog.info(DOMAIN, TAG, `Video prepared, duration: ${info.duration}s`)
            this.duration = Math.floor(info.duration * 1000)
            this.onPrepared?.()
          })
          .onUpdate((info: PlaybackInfo) => {
            this.currentPosition = Math.floor(info.time * 1000)
            this.onProgressUpdate?.(this.currentPosition, this.duration)
          })
      } else {
        // 等待投屏占位符
        Column() {
          Text('等待投屏...')
            .fontSize(14)
            .fontColor(AppColors.TEXT_TERTIARY)
        }
        .width('100%')
        .aspectRatio(16 / 9)
        .backgroundColor(AppColors.BG_ELEVATED)
        .borderRadius(12)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }

      // 进度信息
      if (this.duration > 0) {
        Row() {
          Text(this.formatTime(this.currentPosition))
            .fontSize(12)
            .fontColor(AppColors.TEXT_SECONDARY)

          Blank()

          Text(this.formatTime(this.duration))
            .fontSize(12)
            .fontColor(AppColors.TEXT_SECONDARY)
        }
        .width('100%')
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(16)
    .onAppear(() => {
      // 设置控制器引用
      if (this.controller) {
        this.controller.cardInstance = this
      }
    })
  }

  /**
   * 格式化时间
   */
  private formatTime(ms: number): string {
    const totalSeconds = Math.max(0, Math.floor(ms / 1000))
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60

    if (hours > 0) {
      return `${hours}:${this.pad2(minutes)}:${this.pad2(seconds)}`
    }
    return `${this.pad2(minutes)}:${this.pad2(seconds)}`
  }

  /**
   * 数字补零
   */
  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }
}
