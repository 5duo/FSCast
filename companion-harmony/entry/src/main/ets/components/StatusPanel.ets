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
  // 连接状态（从父组件传入的props）
  @Prop @Watch('onLocalIpAddressChange') localIpAddress: string = ''  // 本机IP地址
  @Prop webSocketConnected: boolean = false
  @Prop carIpAddress: string = ''  // 车机IP地址
  @Prop carConnectionStatus: string = '未连接'
  @Prop networkStatus: string = 'unknown'

  // 本地状态镜像变量，用于触发UI更新
  @State private displayLocalIp: string = ''

  aboutToAppear(): void {
    hilog.info(DOMAIN, TAG, `StatusPanel初始化: localIpAddress=${this.localIpAddress}`)
    this.displayLocalIp = this.localIpAddress
  }

  // 监听localIpAddress变化
  onLocalIpAddressChange(): void {
    hilog.info(DOMAIN, TAG, `✓ localIpAddress变化: ${this.localIpAddress}`)
    this.displayLocalIp = this.localIpAddress
  }

  // DLNA状态
  @Prop dlnaUri: string = ''
  @Prop dlnaIsPlaying: boolean = false
  @Prop dlnaPosition: number = 0
  @Prop dlnaDuration: number = 0

  // 最后连接时间
  @Prop lastConnectionTime: number = 0

  // 自动扫描状态
  @Prop isAutoScanning: boolean = false
  @Prop autoScanProgress: string = ''

  // 回调函数
  onConfigureIp?: () => void  // 长按打开IP设置

  build() {
    Column() {
      // 网络信息组
      Column() {
        // 分组标题
        Text('网络信息')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(AppColors.TEXT_SECONDARY)
          .width('100%')
          .margin({ bottom: AppSizes.SPACING_SM })

        // 自动扫描进度
        if (this.isAutoScanning || this.autoScanProgress.length > 0) {
          this.StatusItemWithIcon(
            SysIcons.SEARCH,
            '自动扫描',
            this.autoScanProgress,
            this.isAutoScanning ? AppColors.PRIMARY : AppColors.TEXT_SECONDARY
          )
        }

        // 网络状态（本机IP）
        this.StatusItem(
          '',
          '本机IP',
          this.displayLocalIp && this.displayLocalIp.length > 0 ? this.displayLocalIp : '未获取到IP',
          this.displayLocalIp && this.displayLocalIp.length > 0 ? AppColors.TEXT_SECONDARY : AppColors.TEXT_DISABLED
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
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(AppSizes.RADIUS_LG)
    .gesture(
      LongPressGesture({ repeat: false })
        .onAction(() => {
          hilog.info(DOMAIN, TAG, '状态面板长按，打开IP设置')
          this.onConfigureIp?.()
        })
    )
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

      // 自动扫描进度
      if (this.isAutoScanning || this.autoScanProgress.length > 0) {
        this.StatusItemWithIcon(
          SysIcons.SEARCH,
          '自动扫描',
          this.autoScanProgress,
          this.isAutoScanning ? AppColors.PRIMARY : AppColors.TEXT_SECONDARY
        )
      }

      // WebSocket 状态
      this.StatusItem(
        '',
        'WebSocket',
        this.webSocketConnected ? '已连接' : '未连接',
        this.webSocketConnected ? AppColors.SUCCESS : AppColors.NEUTRAL
      )

      // 网络状态（本机IP）
      this.StatusItem(
        '',
        '本机IP',
        this.localIpAddress && this.localIpAddress.length > 0 ? this.localIpAddress : '未获取到IP',
        this.localIpAddress && this.localIpAddress.length > 0 ? AppColors.TEXT_SECONDARY : AppColors.TEXT_DISABLED
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
    // 显示本机IP
    hilog.debug(DOMAIN, TAG, `getNetworkStatusText调用: displayLocalIp="${this.displayLocalIp}", 长度=${this.displayLocalIp.length}`)
    if (this.displayLocalIp && this.displayLocalIp.length > 0) {
      return this.displayLocalIp
    }
    return '未获取到IP'
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
