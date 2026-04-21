/**
 * 连接状态卡片组件（现代简洁风格）
 * 整合连接状态、IP信息、控制按钮
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors } from '../constants/AppColors'

const TAG = 'ConnectionCard'
const DOMAIN = 0x0000

@Component
export struct ConnectionCard {
  // 连接状态
  @Prop isConnected: boolean = false
  @Prop isConnecting: boolean = false
  @Prop carIpAddress: string = ''
  @Prop localIpAddress: string = ''
  @Prop isRefreshing: boolean = false
  @Prop scanProgress: string = ''

  // 回调
  onSetIp?: () => void
  onRefresh?: () => void

  build() {
    Column({ space: 12 }) {
      // 连接状态栏
      Row() {
        // 状态图标
        Circle()
          .width(12)
          .height(12)
          .fill(
            this.isConnected ? AppColors.CONNECTED_COLOR :
            this.isConnecting ? AppColors.CONNECTING_COLOR :
            AppColors.DISCONNECTED_COLOR
          )

        Text(
          this.isConnected ? `已连接: ${this.carIpAddress}` :
          this.isConnecting ? '连接中...' :
          '未连接'
        )
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(
            this.isConnected ? AppColors.CONNECTED_COLOR :
            this.isConnecting ? AppColors.CONNECTING_COLOR :
            AppColors.TEXT_TERTIARY
          )
          .margin({ left: 8 })

        Blank()

        // 刷新指示器
        if (this.isRefreshing) {
          LoadingProgress()
            .width(16)
            .height(16)
            .color(AppColors.PRIMARY_COLOR)
        }
      }
      .width('100%')
      .padding(12)
      .backgroundColor(AppColors.BG_ELEVATED)
      .borderRadius(8)

      // IP信息栏
      Column({ space: 8 }) {
        // 本机IP
        Row({ space: 8 }) {
          Text('手机')
            .fontSize(12)
            .fontColor(AppColors.TEXT_TERTIARY)
            .width(50)

          Text(this.localIpAddress)
            .fontSize(12)
            .fontColor(AppColors.TEXT_PRIMARY)
            .layoutWeight(1)
        }
        .width('100%')

        // 车机IP
        Row({ space: 8 }) {
          Text('车机')
            .fontSize(12)
            .fontColor(AppColors.TEXT_TERTIARY)
            .width(50)

          Text(this.carIpAddress)
            .fontSize(12)
            .fontColor(AppColors.TEXT_PRIMARY)
            .layoutWeight(1)
        }
        .width('100%')

        // 扫描进度
        if (this.scanProgress.length > 0) {
          Text(this.scanProgress)
            .fontSize(11)
            .fontColor(AppColors.PRIMARY_COLOR)
            .width('100%')
            .textAlign(TextAlign.Center)
        }
      }
      .width('100%')
      .padding(12)
      .backgroundColor(AppColors.BG_ELEVATED)
      .borderRadius(8)

      // 控制按钮
      Row({ space: 12 }) {
        Button('设置IP')
          .type(ButtonType.Normal)
          .fontSize(14)
          .fontColor(AppColors.TEXT_PRIMARY)
          .backgroundColor(AppColors.BG_ELEVATED)
          .borderRadius(8)
          .height(40)
          .layoutWeight(1)
          .onClick(() => {
            hilog.info(DOMAIN, TAG, 'Set IP button clicked')
            this.onSetIp?.()
          })

        Button(this.isConnected ? '重新连接' : '刷新连接')
          .type(ButtonType.Normal)
          .fontSize(14)
          .fontColor(AppColors.TEXT_PRIMARY)
          .backgroundColor(AppColors.PRIMARY_COLOR)
          .borderRadius(8)
          .height(40)
          .layoutWeight(1)
          .enabled(!this.isConnecting)
          .opacity(this.isConnecting ? 0.6 : 1.0)
          .onClick(() => {
            hilog.info(DOMAIN, TAG, 'Refresh button clicked')
            this.onRefresh?.()
          })
      }
      .width('100%')
    }
    .width('100%')
    .padding(16)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(16)
  }
}
