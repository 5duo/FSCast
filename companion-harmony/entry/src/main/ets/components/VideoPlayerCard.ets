/**
 * 视频播放器卡片组件
 * 统一的视频播放器，支持HTTP头传递和进度同步
 */
import { hilog } from '@kit.PerformanceAnalysisKit'

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
  videoController: VideoController = new VideoController()

  // 回调函数
  onPlayStateChange?: (isPlaying: boolean) => void
  onProgressUpdate?: (position: number, duration: number) => void
  onCompletion?: () => void
  onError?: (error: ESObject) => void

  build() {
    Column() {
      // 标题和状态
      Row() {
        Text('视频播放')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor('#F1F5F9')

        Blank()

        // 播放状态指示
        Row({ space: 6 }) {
          Circle()
            .width(8)
            .height(8)
            .fill(this.dlnaIsPlaying ? '#10B981' : '#64748B')
          Text(this.dlnaIsPlaying ? '播放中' : '已暂停')
            .fontSize(12)
            .fontColor(this.dlnaIsPlaying ? '#10B981' : '#64748B')
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
          .borderRadius(12)
          .autoPlay(true)
          .controls(true)
          .loop(false)
          .muted(false)
          .objectFit(ImageFit.Contain)
          .onStart(() => {
            hilog.info(DOMAIN, TAG, '视频开始播放')
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
          .onError((error) => {
            hilog.error(DOMAIN, TAG, `视频播放错误: ${JSON.stringify(error)}`)
            this.onError?.(error)
          })
          .onPrepared((info: PreparedInfo) => {
            hilog.info(DOMAIN, TAG, `视频准备完成，时长: ${info.duration}ms`)
            this.onProgressUpdate?.(0, info.duration)
          })
          .onSeeking((time) => {
            hilog.debug(DOMAIN, TAG, `视频跳转到: ${time}ms`)
          })
          .onSeeked((info: PlaybackInfo) => {
            hilog.debug(DOMAIN, TAG, `视频跳转完成: ${info.time}ms`)
            this.onProgressUpdate?.(info.time, this.dlnaDuration)
          })
          .onUpdate((info: PlaybackInfo) => {
            // 更新播放进度
            this.onProgressUpdate?.(info.time, this.dlnaDuration)
          })
      } else {
        // 等待URL占位符
        Column() {
          Text('⏸')
            .fontSize(48)
            .fontColor('#64748B')
            .margin({ bottom: 12 })
          Text('等待DLNA投屏...')
            .fontSize(14)
            .fontColor('#94A3B8')
        }
        .width('100%')
        .aspectRatio(16 / 9)
        .backgroundColor('#0F172A')
        .borderRadius(12)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      }

      // 进度信息
      if (this.dlnaDuration > 0) {
        Row() {
          Text(this.formatTime(this.dlnaPosition))
            .fontSize(12)
            .fontColor('#94A3B8')
          Blank()
          Text(this.formatTime(this.dlnaDuration))
            .fontSize(12)
            .fontColor('#94A3B8')
        }
        .width('100%')
        .margin({ top: 8 })
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
  }

  /**
   * 格式化时间
   */
  private formatTime(ms: number): string {
    let totalSeconds = Math.max(0, Math.floor(ms / 1000))
    let hours = Math.floor(totalSeconds / 3600)
    let minutes = Math.floor((totalSeconds % 3600) / 60)
    let seconds = totalSeconds % 60

    if (hours > 0) {
      return `${hours}:${this.pad2(minutes)}:${this.pad2(seconds)}`
    }
    return `${this.pad2(minutes)}:${this.pad2(seconds)}`
  }

  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }
}
