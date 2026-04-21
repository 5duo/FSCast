/**
 * 视频播放器卡片组件（优化版）
 * 使用原生Video控件，自带进度条和时间显示
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors } from '../constants/AppColors'

// 内联尺寸常量
const SPACING_SM = 8
const SPACING_MD = 12
const SPACING_LG = 16
const RADIUS_MD = 8
const RADIUS_LG = 12

const TAG = 'VideoPlayerCard'
const DOMAIN = 0x0000

@Component
export struct VideoPlayerCard {
  // 视频URI和HTTP头
  @Prop dlnaUri: string = ''
  @Prop dlnaHttpHeaders: Record<string, string> = {}

  // 播放状态
  @Prop dlnaIsPlaying: boolean = false
  @Prop dlnaPosition: number = 0
  @Prop dlnaDuration: number = 0

  // 视频控制器
  private videoController: VideoController = new VideoController()

  // 回调函数
  onPlayStateChange?: (isPlaying: boolean) => void
  onProgressUpdate?: (position: number, duration: number) => void
  onCompletion?: () => void
  onError?: (error: ESObject) => void
  onControllerReady?: (controller: VideoController) => void  // 新增：controller准备好的回调

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
      }
      .width('100%')
      .margin({ bottom: 12 })

      // 视频播放器
      if (this.dlnaUri.length > 0) {
        Video({
          src: this.dlnaUri,
          controller: this.videoController
        })
          .width('100%')
          .aspectRatio(16 / 9)
          .backgroundColor('#000000')
          .borderRadius(8)
          .autoPlay(true)
          .controls(true)
          .loop(false)
          .muted(false)
          .objectFit(ImageFit.Contain)
          .onStart(() => {
            hilog.info(DOMAIN, TAG, '视频开始播放')
            // 通知父组件controller已准备好
            this.onControllerReady?.(this.videoController)
            this.onPlayStateChange?.(true)
          })
          .onPause(() => {
            hilog.info(DOMAIN, TAG, '视频暂停')
            this.onPlayStateChange?.(false)
          })
          .onFinish(() => {
            hilog.info(DOMAIN, TAG, '视频播放完成')
            this.onPlayStateChange?.(false)
            this.onCompletion?.()
          })
          .onError((error: ESObject) => {
            hilog.error(DOMAIN, TAG, `视频播放错误: ${JSON.stringify(error)}`)
            this.onError?.(error)
          })
          .onUpdate((info: PlaybackInfo) => {
            // 原生控件已显示进度和时间，这里只回调给父组件用于状态同步
            this.onProgressUpdate?.(info.time, this.dlnaDuration)
          })
      } else {
        // 等待URL占位符
        Column() {
          // 暂停图标
          Text('⏸')
            .fontSize(48)
            .fontColor(AppColors.TEXT_DISABLED)
            .margin({ bottom: 12 })

          // 提示文字
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
    }
    .width('100%')
    .padding(16)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(12)
  }
}
