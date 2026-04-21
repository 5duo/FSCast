/**
 * 实时日志显示卡片
 * 显示WebSocket和控制日志
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors } from '../constants/AppColors'

const TAG = 'LogCard'
const DOMAIN = 0x0000

// 日志条目
interface LogEntry {
  timestamp: string
  level: string  // 'INFO', 'WARN', 'ERROR', 'DEBUG'
  message: string
}

@Component
export struct LogCard {
  @Prop logs: LogEntry[] = []  // 改为@Prop，从外部传入
  @State isExpanded: boolean = true
  private maxLogs: number = 100  // 最多保留100条日志
  private scroller: Scroller = new Scroller()  // 滚动控制器

  // 添加日志
  addLog(level: string, message: string) {
    const now = new Date()
    const timestamp = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`

    const entry: LogEntry = { timestamp, level, message }

    // 添加到数组开头
    this.logs.unshift(entry)

    // 限制日志数量
    if (this.logs.length > this.maxLogs) {
      this.logs = this.logs.slice(0, this.maxLogs)
    }
  }

  build() {
    Column() {
      // 标题栏
      Row() {
        Text('📋 实时日志')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(AppColors.TEXT_PRIMARY)

        Blank()

        // 日志数量
        Text(`${this.logs.length}`)
          .fontSize(11)
          .fontColor(AppColors.TEXT_SECONDARY)
          .padding({ left: 8, right: 8, top: 2, bottom: 2 })
          .backgroundColor(AppColors.BG_ELEMENT)
          .borderRadius(10)

        // 展开/收起按钮
        Button() {
          Text(this.isExpanded ? '▼' : '▲')
            .fontSize(12)
            .fontColor(AppColors.TEXT_PRIMARY)
        }
        .type(ButtonType.Normal)
        .backgroundColor(Color.Transparent)
        .padding(4)
        .onClick(() => {
          this.isExpanded = !this.isExpanded
        })
      }
      .width('100%')
      .margin({ bottom: 8 })

      // 日志内容（展开时显示）
      if (this.isExpanded && this.logs.length > 0) {
        Scroll(this.scroller) {
          Column() {
            ForEach(this.logs, (log: LogEntry, index: number) => {
              Row() {
                // 时间
                Text(log.timestamp)
                  .fontSize(10)
                  .fontColor(AppColors.TEXT_TERTIARY)
                  .fontFamily('monospace')
                  .width(60)

                // 日志级别
                Text(log.level)
                  .fontSize(10)
                  .fontColor(this.getLevelColor(log.level))
                  .fontWeight(FontWeight.Medium)
                  .padding({ left: 4, right: 4, top: 1, bottom: 1 })
                  .backgroundColor(this.getLevelBgColor(log.level))
                  .borderRadius(3)
                  .margin({ left: 4 })

                // 日志消息
                Text(log.message)
                  .fontSize(11)
                  .fontColor(AppColors.TEXT_SECONDARY)
                  .layoutWeight(1)
                  .margin({ left: 8 })
                  .maxLines(2)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
              }
              .width('100%')
              .alignItems(VerticalAlign.Top)
              .margin({ bottom: 6 })
            })
          }
          .width('100%')
          .padding(8)
        }
        .width('100%')
        .height(300)
        .backgroundColor(AppColors.BG_ELEMENT)
        .borderRadius(6)
        .scrollable(ScrollDirection.Vertical)  // 允许垂直滚动
        .scrollBar(BarState.Auto)  // 自动显示滚动条
        .edgeEffect(EdgeEffect.Spring)  // 添加弹性边缘效果
      } else if (this.isExpanded && this.logs.length === 0) {
        // 空状态
        Column() {
          Text('暂无日志')
            .fontSize(12)
            .fontColor(AppColors.TEXT_TERTIARY)
            .margin({ top: 20, bottom: 20 })
        }
        .width('100%')
        .height(60)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
        .backgroundColor(AppColors.BG_ELEMENT)
        .borderRadius(6)
      }
    }
    .width('100%')
    .padding(12)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(8)
  }

  /**
   * 获取日志级别颜色
   */
  private getLevelColor(level: string): string {
    switch (level) {
      case 'INFO':
        return AppColors.SUCCESS
      case 'WARN':
        return '#F59E0B'
      case 'ERROR':
        return '#EF4444'
      case 'DEBUG':
        return AppColors.TEXT_TERTIARY
      default:
        return AppColors.TEXT_SECONDARY
    }
  }

  /**
   * 获取日志级别背景色
   */
  private getLevelBgColor(level: string): string {
    switch (level) {
      case 'INFO':
        return 'rgba(16, 185, 129, 0.1)'
      case 'WARN':
        return 'rgba(245, 158, 11, 0.1)'
      case 'ERROR':
        return 'rgba(239, 68, 68, 0.1)'
      case 'DEBUG':
        return 'rgba(100, 116, 139, 0.05)'
      default:
        return 'rgba(100, 116, 139, 0.05)'
    }
  }

  /**
   * 添加INFO日志
   */
  addInfo(message: string) {
    this.addLog('INFO', message)
  }

  /**
   * 添加WARN日志
   */
  addWarn(message: string) {
    this.addLog('WARN', message)
  }

  /**
   * 添加ERROR日志
   */
  addError(message: string) {
    this.addLog('ERROR', message)
  }

  /**
   * 添加DEBUG日志
   */
  addDebug(message: string) {
    this.addLog('DEBUG', message)
  }

  /**
   * 清空日志
   */
  clearLogs() {
    this.logs = []
  }
}
