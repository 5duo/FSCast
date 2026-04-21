/**
 * 车机端实时进度条卡片
 * 显示车机端的播放进度
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors } from '../constants/AppColors'

const TAG = 'CarProgressCard'
const DOMAIN = 0x0000

@Component
export struct CarProgressCard {
  @Prop carPosition: number = 0  // 车机端播放位置（毫秒）
  @Prop carDuration: number = 0   // 车机端视频总时长（毫秒）
  @Prop carIsPlaying: boolean = false  // 车机端是否正在播放

  build() {
    Column() {
      // 标题
      Row() {
        Text('🚗 车机端进度')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(AppColors.TEXT_PRIMARY)

        Blank()

        // 播放状态指示
        Row({ space: 4 }) {
          Circle()
            .width(6)
            .height(6)
            .fill(this.carIsPlaying ? AppColors.SUCCESS : AppColors.NEUTRAL)

          Text(this.carIsPlaying ? '播放中' : '已暂停')
            .fontSize(11)
            .fontColor(this.carIsPlaying ? AppColors.SUCCESS : AppColors.NEUTRAL)
        }
      }
      .width('100%')
      .margin({ bottom: 10 })

      // 时间显示
      Row() {
        Text(this.formatTime(this.carPosition))
          .fontSize(13)
          .fontColor(AppColors.TEXT_PRIMARY)
          .fontFamily('monospace')

        Text(` / ${this.formatTime(this.carDuration)}`)
          .fontSize(13)
          .fontColor(AppColors.TEXT_SECONDARY)
          .fontFamily('monospace')
      }
      .width('100%')
      .margin({ bottom: 8 })

      // 进度条
      Column() {
        // 进度条背景
        Row() {
          // 已播放部分
          Row()
            .width(this.carDuration > 0 ? `${(this.carPosition / this.carDuration * 100).toFixed(1)}%` : '0%')
            .height(6)
            .backgroundColor(AppColors.PRIMARY)
            .borderRadius(3)
        }
        .width('100%')
        .height(6)
        .backgroundColor(AppColors.BG_ELEMENT)
        .borderRadius(3)
      }
      .width('100%')

      // 百分比显示
      Row() {
        Text(`${this.carDuration > 0 ? (this.carPosition / this.carDuration * 100).toFixed(1) : '0.0'}%`)
          .fontSize(11)
          .fontColor(AppColors.TEXT_SECONDARY)
          .fontFamily('monospace')

        Blank()

        Text(`距结束: ${this.formatTime(Math.max(0, this.carDuration - this.carPosition))}`)
          .fontSize(11)
          .fontColor(AppColors.TEXT_SECONDARY)
      }
      .width('100%')
      .margin({ top: 8 })
    }
    .width('100%')
    .padding(12)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(8)
  }

  /**
   * 格式化时间（毫秒 -> 分:秒）
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

  /**
   * 数字补零
   */
  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }
}
