/**
 * 状态面板组件（优化版）
 * 使用兼容性图标组件
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { SysIcons } from '../constants/IconResources'
import { TextIcon } from './IconCompat'

const TAG = 'StatusPanel'
const DOMAIN = 0x0000

// ==================== 颜色常量 ====================
export class AppColors {
  static readonly PRIMARY = '#6366F1'        // 主色调 - 靛蓝
  static readonly SUCCESS = '#10B981'        // 成功 - 绿色
  static readonly WARNING = '#F59E0B'        // 警告 - 橙色
  static readonly ERROR = '#EF4444'          // 错误 - 红色
  static readonly NEUTRAL = '#64748B'        // 中性 - 灰色

  static readonly BG_PRIMARY = '#0F172A'     // 主背景 - 深蓝灰
  static readonly BG_CARD = '#1E293B'        // 卡片背景 - 中蓝灰
  static readonly BG_ELEMENT = '#334155'     // 元素背景 - 浅蓝灰

  static readonly TEXT_PRIMARY = '#F1F5F9'   // 主要文本
  static readonly TEXT_SECONDARY = '#94A3B8' // 次要文本
  static readonly TEXT_DISABLED = '#475569'  // 禁用文本
}

// ==================== 尺寸常量 ====================
export class AppSizes {
  static readonly SPACING_XS = 4
  static readonly SPACING_SM = 8
  static readonly SPACING_MD = 12
  static readonly SPACING_LG = 16
  static readonly SPACING_XL = 24

  static readonly RADIUS_SM = 8
  static readonly RADIUS_MD = 12
  static readonly RADIUS_LG = 16

  static readonly ICON_SM = 16
  static readonly ICON_MD = 20
  static readonly ICON_LG = 24
}

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
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(AppSizes.RADIUS_LG)
    .animation({
      duration: 300,
      curve: Curve.EaseInOut
    })
  }

  /**
   * 顶部状态条（优化版）
   */
  @Builder StatusBar() {
    Row() {
      // 左侧：应用名和连接状态
      Row({ space: AppSizes.SPACING_MD }) {
        // 应用标题 - 加粗更醒目
        Text('FSCast')
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(AppColors.TEXT_PRIMARY)

        // 连接状态指示器
        Row({ space: 6 }) {
          Circle()
            .width(8)
            .height(8)
            .fill(this.webSocketConnected ? AppColors.SUCCESS : AppColors.NEUTRAL)

          Text(this.carConnectionStatus)
            .fontSize(13)
            .fontColor(this.webSocketConnected ? AppColors.SUCCESS : AppColors.NEUTRAL)
            .fontWeight(FontWeight.Medium)
        }
        .padding({
          left: AppSizes.SPACING_SM,
          right: AppSizes.SPACING_SM,
          top: 4,
          bottom: 4
        })
        .backgroundColor(AppColors.BG_ELEMENT)
        .borderRadius(AppSizes.RADIUS_MD)
      }

      Blank()

      // 右侧：刷新按钮和展开图标
      Row({ space: AppSizes.SPACING_SM }) {
        // 刷新按钮 - 使用兼容性图标
        Button() {
          TextIcon({
            iconKey: 'REFRESH',
            fontSize: AppSizes.ICON_MD,
            fontColor: AppColors.TEXT_SECONDARY
          })
        }
        .width(36)
        .height(36)
        .backgroundColor(AppColors.BG_ELEMENT)
        .borderRadius(AppSizes.RADIUS_SM)
        .type(ButtonType.Normal)
        .onClick(() => {
          hilog.info(DOMAIN, TAG, '用户点击刷新按钮')
          this.onRefresh?.()
        })
        .stateEffect(true)

        // 展开/收起按钮 - 使用兼容性图标
        Button() {
          TextIcon({
            iconKey: this.isExpanded ? 'COLLAPSE' : 'EXPAND',
            fontSize: 18,
            fontColor: AppColors.TEXT_SECONDARY
          })
        }
        .width(36)
        .height(36)
        .backgroundColor(AppColors.BG_ELEMENT)
        .borderRadius(AppSizes.RADIUS_SM)
        .type(ButtonType.Normal)
        .onClick(() => {
          this.isExpanded = !this.isExpanded
          hilog.info(DOMAIN, TAG, `状态面板${this.isExpanded ? '展开' : '收起'}`)
        })
        .stateEffect(true)
      }
    }
    .width('100%')
    .height(56)
    .padding({
      left: AppSizes.SPACING_LG,
      right: AppSizes.SPACING_LG
    })
    .alignItems(VerticalAlign.Center)
  }

  /**
   * 详细状态面板（优化版）
   */
  @Builder DetailStatusPanel() {
    Column() {
      // 连接状态组
      this.ConnectionStatusGroup()

      Divider()
        .color(AppColors.BG_ELEMENT)
        .strokeWidth(1)
        .margin({
          top: AppSizes.SPACING_MD,
          bottom: AppSizes.SPACING_MD
        })

      // 播放状态组
      this.PlaybackStatusGroup()
    }
    .width('100%')
    .padding({
      left: AppSizes.SPACING_LG,
      right: AppSizes.SPACING_LG,
      top: AppSizes.SPACING_SM,
      bottom: AppSizes.SPACING_XL
    })
  }

  /**
   * 连接状态组（优化版）
   */
  @Builder ConnectionStatusGroup() {
    Column() {
      // 分组标题
      Text('连接状态')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor(AppColors.TEXT_SECONDARY)
        .width('100%')
        .margin({ bottom: AppSizes.SPACING_SM })

      // WebSocket 状态
      this.StatusItem(
        '', // 不使用图标，直接用状态颜色
        'WebSocket',
        this.getConnectionText(),
        this.getConnectionColor()
      )

      // 网络状态
      this.StatusItem(
        '', // 不使用图标，直接用状态颜色
        '网络',
        this.getNetworkStatusText(),
        this.getNetworkStatusColor()
      )

      // 最后连接时间
      if (this.lastConnectionTime > 0) {
        this.StatusItemWithIcon(
          SysIcons.CLOCK,
          '最后连接',
          this.formatTimestamp(this.lastConnectionTime),
          AppColors.TEXT_SECONDARY
        )
      }
    }
    .width('100%')
  }

  /**
   * 播放状态组（优化版）
   */
  @Builder PlaybackStatusGroup() {
    Column() {
      // 分组标题
      Text('播放状态')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor(AppColors.TEXT_SECONDARY)
        .width('100%')
        .margin({ bottom: AppSizes.SPACING_SM })

      if (this.dlnaUri.length > 0) {
        // 播放器状态
        this.StatusItemWithIcon(
          this.dlnaIsPlaying ? SysIcons.PLAY : SysIcons.PAUSE,
          '播放器',
          this.dlnaIsPlaying ? '播放中' : '已暂停',
          this.dlnaIsPlaying ? AppColors.SUCCESS : AppColors.NEUTRAL
        )

        // 播放进度
        this.StatusItemWithIcon(
          SysIcons.CLOCK,
          '进度',
          `${this.formatTime(this.dlnaPosition)} / ${this.formatTime(this.dlnaDuration)}`,
          AppColors.TEXT_SECONDARY
        )
      } else {
        // 等待投屏提示
        Text('等待投屏...')
          .fontSize(14)
          .fontColor(AppColors.TEXT_SECONDARY)
          .width('100%')
          .textAlign(TextAlign.Center)
          .padding({ top: AppSizes.SPACING_SM, bottom: AppSizes.SPACING_SM })
      }
    }
    .width('100%')
  }

  /**
   * 状态项（优化版）
   */
  @Builder StatusItem(icon: string, label: string, value: string, valueColor: string) {
    Row() {
      // 图标符号（为空时不显示）
      if (icon.length > 0) {
        Text(icon)
          .fontSize(14)
          .fontColor(valueColor)
          .fontWeight(FontWeight.Medium)
      }

      // 标签
      Text(label)
        .fontSize(13)
        .fontColor(AppColors.TEXT_SECONDARY)
        .margin({ left: icon.length > 0 ? AppSizes.SPACING_SM : 0 })

      Blank()

      // 值
      Text(value)
        .fontSize(13)
        .fontColor(valueColor)
        .fontWeight(FontWeight.Medium)
    }
    .width('100%')
    .margin({
      top: AppSizes.SPACING_XS,
      bottom: AppSizes.SPACING_XS
    })
    .padding({
      left: AppSizes.SPACING_XS,
      right: AppSizes.SPACING_XS,
      top: 4,
      bottom: 4
    })
  }

  /**
   * 状态项（带图标）
   */
  @Builder StatusItemWithIcon(iconName: string, label: string, value: string, valueColor: string) {
    Row() {
      // 兼容性图标
      TextIcon({
        iconKey: iconName,
        fontSize: 14,
        fontColor: valueColor
      })

      // 标签
      Text(label)
        .fontSize(13)
        .fontColor(AppColors.TEXT_SECONDARY)
        .margin({ left: AppSizes.SPACING_SM })

      Blank()

      // 值
      Text(value)
        .fontSize(13)
        .fontColor(valueColor)
        .fontWeight(FontWeight.Medium)
    }
    .width('100%')
    .margin({
      top: AppSizes.SPACING_XS,
      bottom: AppSizes.SPACING_XS
    })
    .padding({
      left: AppSizes.SPACING_XS,
      right: AppSizes.SPACING_XS,
      top: 4,
      bottom: 4
    })
  }

  /**
   * 获取连接状态颜色
   */
  private getConnectionColor(): string {
    if (this.webSocketConnected) {
      return AppColors.SUCCESS
    }
    return AppColors.NEUTRAL
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
      return AppColors.SUCCESS
    } else if (this.networkStatus === 'lost') {
      return AppColors.ERROR
    }
    return AppColors.NEUTRAL
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
   * 格式化时间戳
   */
  private formatTimestamp(timestamp: number): string {
    const date = new Date(timestamp)
    const hours = date.getHours()
    const minutes = date.getMinutes()
    const seconds = date.getSeconds()
    return `${this.pad2(hours)}:${this.pad2(minutes)}:${this.pad2(seconds)}`
  }

  /**
   * 数字补零
   */
  private pad2(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
  }
}
