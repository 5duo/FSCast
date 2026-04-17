/**
 * 底部控制面板组件
 * 包含重连按钮、IP设置和服务状态快速查看
 */
import { hilog } from '@kit.PerformanceAnalysisKit'

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
  onConfigureIp?: () => void

  build() {
    Column() {
      // 标题
      Text('快速操作')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor('#F1F5F9')
        .width('100%')
        .margin({ bottom: 12 })

      // 连接控制区域
      Row() {
        // 连接状态指示
        Column() {
          Row({ space: 8 }) {
            Circle()
              .width(12)
              .height(12)
              .fill(this.getConnectionColor())
            Text(this.getConnectionStatusText())
              .fontSize(14)
              .fontColor('#F1F5F9')
          }

          if (this.webSocketConnected) {
            Text(this.carIpAddress)
              .fontSize(12)
              .fontColor('#94A3B8')
              .margin({ top: 4 })
          }
        }
        .alignItems(HorizontalAlign.Start)

        Blank()

        // 重新连接按钮
        Button(this.isConnecting ? '连接中...' : '重新连接')
          .fontSize(14)
          .fontColor('#FFFFFF')
          .backgroundColor('#6366F1')
          .borderRadius(8)
          .padding({ left: 16, right: 16, top: 8, bottom: 8 })
          .enabled(!this.isConnecting)
          .opacity(this.isConnecting ? 0.6 : 1)
          .onClick(() => {
            hilog.info(DOMAIN, TAG, '用户点击重新连接按钮')
            this.onReconnect?.()
          })
      }
      .width('100%')
      .padding(12)
      .backgroundColor('#0F172A')
      .borderRadius(12)
      .margin({ bottom: 12 })

      // IP设置和服务状态
      Row() {
        // IP设置按钮
        Button() {
          Row({ space: 8 }) {
            Text('⚙️')
              .fontSize(16)
            Text('设置车机IP')
              .fontSize(13)
              .fontColor('#F1F5F9')
          }
        }
        .type(ButtonType.Normal)
        .backgroundColor('#0F172A')
        .borderRadius(8)
        .padding({ left: 12, right: 12, top: 8, bottom: 8 })
        .onClick(() => {
          hilog.info(DOMAIN, TAG, '用户点击设置车机IP按钮')
          this.onConfigureIp?.()
        })

        Blank()

        // 应用状态提示
        Text('FSCast Remote v0.3.0')
          .fontSize(11)
          .fontColor('#64748B')
      }
      .width('100%')
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
  }

  /**
   * 服务状态指示点
   */
  @Builder ServiceStatusDot(label: string, isRunning: boolean) {
    Row({ space: 4 }) {
      Circle()
        .width(6)
        .height(6)
        .fill(isRunning ? '#10B981' : '#64748B')
      Text(label)
        .fontSize(11)
        .fontColor('#94A3B8')
    }
  }

  /**
   * 获取连接状态颜色
   */
  private getConnectionColor(): string {
    if (this.isConnecting) {
      return '#F59E0B'  // 橙色 - 连接中
    }
    if (this.webSocketConnected) {
      return '#10B981'  // 绿色 - 已连接
    }
    return '#EF4444'    // 红色 - 未连接
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
