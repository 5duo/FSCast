/**
 * 底部控制面板组件（优化版）
 * 使用兼容性图标组件
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors, AppSizes } from './StatusPanel'
import { SysIcons } from '../constants/IconResources'
import { TextIcon } from './IconCompat'

const TAG = 'ControlPanel'
const DOMAIN = 0x0000

@Component
export struct ControlPanel {
  // 连接状态
  @Prop webSocketConnected: boolean = false
  @Prop carIpAddress: string = ''

  // 连接中状态
  @Prop isConnecting: boolean = false

  // 回调函数
  onReconnect?: () => void

  build() {
    Column() {
      // 标题
      Text('快速操作')
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(AppColors.TEXT_PRIMARY)
        .width('100%')
        .margin({ bottom: AppSizes.SPACING_MD })

      // 连接控制区域
      Row() {
        // 左侧：连接状态指示
        Column() {
          Row({ space: AppSizes.SPACING_SM }) {
            // 状态指示点
            Circle()
              .width(12)
              .height(12)
              .fill(this.getConnectionColor())

            // 状态文本
            Text(this.getConnectionStatusText())
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(AppColors.TEXT_PRIMARY)
          }

          // 已连接时显示 IP 地址
          if (this.webSocketConnected) {
            Text(this.carIpAddress)
              .fontSize(12)
              .fontColor(AppColors.TEXT_SECONDARY)
              .margin({ top: 4 })
          }
        }
        .alignItems(HorizontalAlign.Start)

        Blank()

        // 右侧：重新连接按钮
        Button(this.isConnecting ? '连接中...' : '重新连接')
          .fontSize(14)
          .fontColor('#FFFFFF')
          .backgroundColor(AppColors.PRIMARY)
          .borderRadius(AppSizes.RADIUS_MD)
          .padding({
            left: AppSizes.SPACING_LG,
            right: AppSizes.SPACING_LG,
            top: AppSizes.SPACING_SM,
            bottom: AppSizes.SPACING_SM
          })
          .enabled(!this.isConnecting)
          .opacity(this.isConnecting ? 0.6 : 1.0)
          .onClick(() => {
            hilog.info(DOMAIN, TAG, '用户点击重新连接按钮')
            this.onReconnect?.()
          })
          .stateEffect(true)
      }
      .width('100%')
      .padding(AppSizes.SPACING_MD)
      .backgroundColor(AppColors.BG_ELEMENT)
      .borderRadius(AppSizes.RADIUS_MD)
      .margin({ bottom: AppSizes.SPACING_MD })

      // 应用版本信息
      Row() {
        Text('FSCast Remote v1.0.0')
          .fontSize(11)
          .fontColor(AppColors.TEXT_DISABLED)
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
    }
    .width('100%')
    .padding(AppSizes.SPACING_LG)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(AppSizes.RADIUS_LG)
  }

  /**
   * 获取连接状态颜色
   */
  private getConnectionColor(): string {
    if (this.isConnecting) {
      return AppColors.WARNING  // 橙色 - 连接中
    }
    if (this.webSocketConnected) {
      return AppColors.SUCCESS  // 绿色 - 已连接
    }
    return AppColors.ERROR      // 红色 - 未连接
  }

  /**
   * 获取连接状态文本
   */
  private getConnectionStatusText(): string {
    if (this.isConnecting) {
      return '正在连接...'
    }
    if (this.webSocketConnected) {
      return '已连接'
    }
    return '未连接'
  }
}
