/**
 * 状态面板组件
 * 折叠式设计，默认显示简化状态，点击展开显示详细状态
 */
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'StatusPanel'
const DOMAIN = 0x0000

// 状态颜色常量
export class StatusColor {
  static readonly SUCCESS: string = '#10B981'  // 绿色 - 已连接/运行中
  static readonly WARNING: string = '#F59E0B'  // 橙色 - 连接中/警告
  static readonly NEUTRAL: string = '#64748B'  // 灰色 - 未连接/停止
  static readonly ERROR: string = '#EF4444'    // 红色 - 错误
}

export const STATUS_COLOR = StatusColor

@Component
export struct StatusPanel {
  // 连接状态
  @Prop webSocketConnected: boolean = false
  @Prop carConnectionStatus: string = '未连接'
  @Prop networkStatus: string = 'unknown'

  // DLNA状态
  @Prop dlnaUri: string = ''
  @Prop dlnaIsPlaying: boolean = false
  @Prop dlnaPosition: number = 0
  @Prop dlnaDuration: number = 0

  // 服务状态（已移除DLNA服务，不再需要）

  // 最后连接时间
  @Prop lastConnectionTime: number = 0

  // 面板展开状态
  @Link isExpanded: boolean

  // 回调函数
  onRefresh?: () => void

  build() {
    Column() {
      // 顶部状态条
      this.StatusBar()

      // 展开的详细状态
      if (this.isExpanded) {
        this.DetailStatusPanel()
      }
    }
    .width('100%')
    .backgroundColor('#1E293B')
    .borderRadius(16)
    .animation({
      duration: 300,
      curve: Curve.EaseInOut
    })
  }

  /**
   * 顶部状态条
   */
  @Builder StatusBar() {
    Row() {
      // 左侧：应用名和连接状态
      Row({ space: 12 }) {
        Text('FSCast Remote')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor('#F1F5F9')

        // 连接状态指示器
        Row({ space: 6 }) {
          Circle()
            .width(8)
            .height(8)
            .fill(this.webSocketConnected ? StatusColor.SUCCESS : StatusColor.NEUTRAL)
          Text(this.carConnectionStatus)
            .fontSize(12)
            .fontColor(this.webSocketConnected ? StatusColor.SUCCESS : StatusColor.NEUTRAL)
        }
        .padding({ left: 8, right: 8, top: 4, bottom: 4 })
        .backgroundColor('#0F172A')
        .borderRadius(12)
      }

      Blank()

      // 右侧：刷新按钮和展开图标
      Row({ space: 8 }) {
        // 刷新按钮
        Button() {
          Text('🔄')
            .fontSize(16)
        }
        .width(32)
        .height(32)
        .backgroundColor('#0F172A')
        .borderRadius(16)
        .type(ButtonType.Normal)
        .onClick(() => {
          hilog.info(DOMAIN, TAG, '用户点击刷新按钮')
          this.onRefresh?.()
        })

        // 展开/收起图标
        Button() {
          Text(this.isExpanded ? '▲' : '▼')
            .fontSize(14)
            .fontColor('#94A3B8')
        }
        .width(32)
        .height(32)
        .backgroundColor('#0F172A')
        .borderRadius(16)
        .type(ButtonType.Normal)
        .onClick(() => {
          this.isExpanded = !this.isExpanded
          hilog.info(DOMAIN, TAG, `状态面板${this.isExpanded ? '展开' : '收起'}`)
        })
      }
    }
    .width('100%')
    .height(56)
    .padding({ left: 16, right: 16 })
    .alignItems(VerticalAlign.Center)
  }

  /**
   * 详细状态面板
   */
  @Builder DetailStatusPanel() {
    Column() {
      // 连接状态组
      this.ConnectionStatusGroup()

      Divider()
        .color('#334155')
        .strokeWidth(1)
        .margin({ top: 12, bottom: 12 })

      // 播放状态组
      this.PlaybackStatusGroup()
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 8, bottom: 16 })
  }

  /**
   * 连接状态组
   */
  @Builder ConnectionStatusGroup() {
    Column() {
      Text('连接状态')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor('#94A3B8')
        .width('100%')
        .margin({ bottom: 8 })

      this.StatusItem('🔗', 'WebSocket', this.getConnectionText(), this.getConnectionColor())
      this.StatusItem('🌐', '网络', this.getNetworkStatusText(), this.getNetworkStatusColor())
      if (this.lastConnectionTime > 0) {
        this.StatusItem('🕐', '最后连接', this.formatTimestamp(this.lastConnectionTime), '#94A3B8')
      }
    }
    .width('100%')
  }

  /**
   * 播放状态组
   */
  @Builder PlaybackStatusGroup() {
    Column() {
      Text('播放状态')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor('#94A3B8')
        .width('100%')
        .margin({ bottom: 8 })

      if (this.dlnaUri.length > 0) {
        this.StatusItem('▶️', '播放器', this.dlnaIsPlaying ? '播放中' : '已暂停',
          this.dlnaIsPlaying ? StatusColor.SUCCESS : StatusColor.NEUTRAL)
        this.StatusItem('⏱️', '进度',
          `${this.formatTime(this.dlnaPosition)} / ${this.formatTime(this.dlnaDuration)}`,
          '#94A3B8')
      } else {
        Text('等待DLNA投屏...')
          .fontSize(13)
          .fontColor('#94A3B8')
          .width('100%')
          .textAlign(TextAlign.Center)
      }
    }
    .width('100%')
  }

  /**
   * 状态项
   */
  @Builder StatusItem(icon: string, label: string, value: string, valueColor: string) {
    Row() {
      Text(icon)
        .fontSize(14)
      Text(label)
        .fontSize(13)
        .fontColor('#94A3B8')
        .margin({ left: 8 })
      Blank()
      Text(value)
        .fontSize(13)
        .fontColor(valueColor)
    }
    .width('100%')
    .margin({ top: 4, bottom: 4 })
  }

  /**
   * 获取连接状态颜色
   */
  private getConnectionColor(): string {
    if (this.webSocketConnected) {
      return StatusColor.SUCCESS
    }
    return StatusColor.NEUTRAL
  }

  /**
   * 获取连接状态文本
   */
  private getConnectionText(): string {
    if (this.webSocketConnected) {
      return '已连接'
    }
    return '未连接'
  }

  /**
   * 获取网络状态颜色
   */
  private getNetworkStatusColor(): string {
    if (this.networkStatus === 'available') {
      return StatusColor.SUCCESS
    } else if (this.networkStatus === 'lost') {
      return StatusColor.ERROR
    }
    return StatusColor.NEUTRAL
  }

  /**
   * 获取网络状态文本
   */
  private getNetworkStatusText(): string {
    if (this.networkStatus === 'available') {
      return '可用'
    } else if (this.networkStatus === 'lost') {
      return '不可用'
    }
    return '未知'
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

  /**
   * 格式化时间戳
   */
  private formatTimestamp(timestamp: number): string {
    const date = new Date(timestamp)
    const hours = date.getHours()
    const minutes = date.getMinutes()
    const seconds = date.getSeconds()
    return `${this.pad2(hours)}:${this.pad2(minutes)}:${this.pad2(seconds)}`
  }

  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }
}
