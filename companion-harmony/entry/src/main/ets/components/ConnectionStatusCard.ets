/**
 * 连接状态卡片组件
 * 独立显示WebSocket连接状态和本机IP
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors, AppSizes } from './StatusPanel'
import { TextIcon } from './IconCompat'

const TAG = 'ConnectionStatusCard'
const DOMAIN = 0x0000

@Component
export struct ConnectionStatusCard {
  @Prop webSocketConnected: boolean = false
  @Prop carIpAddress: string = ''
  @Prop localIpAddress: string = ''  // 本机IP地址
  @Prop isRefreshing: boolean = false  // 是否正在刷新
  @Prop scanProgress: string = ''  // 扫描进度
  onRefresh?: () => void  // 刷新回调

  build() {
    Column() {
      // 第一行：连接状态
      Row() {
        // 左侧：连接状态
        Row({ space: AppSizes.SPACING_SM }) {
          // 状态指示点
          Circle()
            .width(12)
            .height(12)
            .fill(this.webSocketConnected ? AppColors.SUCCESS : AppColors.NEUTRAL)

          // 连接状态文本
          Text(this.webSocketConnected ? '已连接' : '未连接')
            .fontSize(16)
            .fontColor(this.webSocketConnected ? AppColors.SUCCESS : AppColors.NEUTRAL)
            .fontWeight(FontWeight.Medium)

          // 车机IP地址
          if (this.webSocketConnected && this.carIpAddress.length > 0) {
            Text(`(${this.carIpAddress})`)
              .fontSize(14)
              .fontColor(AppColors.TEXT_SECONDARY)
          }
        }

        Blank()

        // 右侧：刷新按钮（带旋转动效和禁用状态）
        Button() {
          TextIcon({
            iconKey: 'REFRESH',
            fontSize: AppSizes.ICON_MD,
            fontColor: this.isRefreshing ? AppColors.TEXT_DISABLED : AppColors.TEXT_SECONDARY
          })
            .rotate(this.isRefreshing ? { angle: 360 } : { angle: 0 })
            .animation({
              duration: 1000,
              curve: Curve.Linear,
              iterations: this.isRefreshing ? -1 : 1  // 无限旋转或停止
            })
        }
        .width(36)
        .height(36)
        .backgroundColor(AppColors.BG_ELEMENT)
        .borderRadius(AppSizes.RADIUS_SM)
        .type(ButtonType.Normal)
        .enabled(!this.isRefreshing)  // 刷新时禁用按钮
        .opacity(this.isRefreshing ? 0.5 : 1.0)  // 刷新时半透明
        .onClick(() => {
          if (!this.isRefreshing) {
            hilog.info(DOMAIN, TAG, '用户点击刷新按钮')
            this.onRefresh?.()
          }
        })
        .stateEffect(!this.isRefreshing)  // 刷新时不显示点击效果
      }
      .width('100%')

      // 第二行：本机IP（如果有）
      if (this.localIpAddress && this.localIpAddress.length > 0) {
        Row() {
          Text(`本机IP: ${this.localIpAddress}`)
            .fontSize(13)
            .fontColor(AppColors.TEXT_SECONDARY)
        }
        .width('100%')
        .padding({ top: AppSizes.SPACING_SM })
      }

      // 第三行：扫描进度（如果有）
      if (this.scanProgress && this.scanProgress.length > 0) {
        Row() {
          Text(`扫描中: ${this.scanProgress}`)
            .fontSize(13)
            .fontColor(AppColors.PRIMARY)
        }
        .width('100%')
        .padding({ top: AppSizes.SPACING_SM })
      }
    }
    .width('100%')
    .padding(AppSizes.SPACING_LG)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(AppSizes.RADIUS_LG)
  }
}
