/**
 * 视频URL显示卡片组件（优化版）
 * 使用 HarmonyOS SymbolGlyph 系统图标
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { pasteboard } from '@kit.BasicServicesKit'
import { AppColors, AppSizes } from './StatusPanel'
import { SysIcons } from '../constants/IconResources'

const TAG = 'VideoUrlCard'
const DOMAIN = 0x0000

@Component
export struct VideoUrlCard {
  // 视频URI
  @Prop dlnaUri: string = ''

  // HTTP头（用于下载）
  @Prop dlnaHttpHeaders: Record<string, string> = {}

  // 是否正在下载（从父组件传入）
  @Link isDownloading: boolean

  // 下载进度（从父组件传入）
  @Link downloadProgress: number

  // 回调函数
  onCopy?: () => void
  onDownload?: (uri: string, headers: Record<string, string>) => void

  build() {
    Column() {
      // 标题行
      Row() {
        // 标题图标 - 使用系统图标
        SymbolGlyph($r(`sys.symbol.${SysIcons.LINK}`))
          .fontSize(AppSizes.ICON_MD)
          .fontColor([AppColors.PRIMARY])

        // 标题文字
        Text('视频地址')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(AppColors.TEXT_PRIMARY)
          .margin({ left: AppSizes.SPACING_XS })

        Blank()

        // URL状态指示
        if (this.dlnaUri.length > 0) {
          Row({ space: 6 }) {
            Circle()
              .width(8)
              .height(8)
              .fill(AppColors.SUCCESS)

            Text('已接收')
              .fontSize(12)
              .fontColor(AppColors.SUCCESS)
          }
        }
      }
      .width('100%')
      .margin({ bottom: AppSizes.SPACING_MD })

      // URL显示区域
      Column() {
        if (this.dlnaUri.length > 0) {
          // 显示URL
          Text(this.formatUrlForDisplay(this.dlnaUri))
            .fontSize(12)
            .fontColor(AppColors.TEXT_SECONDARY)
            .width('100%')
            .maxLines(3)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .margin({ bottom: AppSizes.SPACING_MD })

          // 按钮区域
          Row({ space: AppSizes.SPACING_MD }) {
            // 复制按钮
            Button() {
              Row({ space: AppSizes.SPACING_SM }) {
                Text('□')
                  .fontSize(AppSizes.ICON_MD)
                  .fontColor(AppColors.TEXT_PRIMARY)

                Text('复制')
                  .fontSize(14)
                  .fontColor(AppColors.TEXT_PRIMARY)
              }
            }
            .type(ButtonType.Normal)
            .backgroundColor(AppColors.BG_ELEMENT)
            .borderRadius(AppSizes.RADIUS_MD)
            .height(40)
            .padding({
              left: AppSizes.SPACING_MD,
              right: AppSizes.SPACING_MD
            })
            .onClick(() => {
              this.copyUrl()
            })
            .stateEffect(true)

            Blank()

            // 下载按钮
            if (this.isDownloading) {
              // 下载中状态
              Row({ space: AppSizes.SPACING_SM }) {
                LoadingProgress()
                  .width(16)
                  .height(16)
                  .color(AppColors.PRIMARY)

                Text(`${this.downloadProgress}%`)
                  .fontSize(13)
                  .fontColor(AppColors.PRIMARY)
              }
              .width(100)
              .height(40)
              .padding({
                left: AppSizes.SPACING_MD,
                right: AppSizes.SPACING_MD
              })
              .backgroundColor(AppColors.BG_PRIMARY)
              .borderRadius(AppSizes.RADIUS_MD)
              .justifyContent(FlexAlign.Center)
            } else {
              Button() {
                Row({ space: AppSizes.SPACING_SM }) {
                  SymbolGlyph($r(`sys.symbol.${SysIcons.DOWNLOAD}`))
                    .fontSize(AppSizes.ICON_MD)
                    .fontColor(['#FFFFFF'])

                  Text('下载')
                    .fontSize(14)
                    .fontColor('#FFFFFF')
                }
              }
              .type(ButtonType.Normal)
              .backgroundColor(AppColors.PRIMARY)
              .borderRadius(AppSizes.RADIUS_MD)
              .height(40)
              .padding({
                left: AppSizes.SPACING_MD,
                right: AppSizes.SPACING_MD
              })
              .onClick(() => {
                this.downloadVideo()
              })
              .stateEffect(true)
            }
          }
          .width('100%')
        } else {
          // 等待接收状态
          Row({ space: AppSizes.SPACING_SM }) {
            Circle()
              .width(8)
              .height(8)
              .fill(AppColors.TEXT_DISABLED)

            Text('等待接收视频地址...')
              .fontSize(13)
              .fontColor(AppColors.TEXT_DISABLED)
          }
          .width('100%')
          .padding(AppSizes.SPACING_MD)
          .backgroundColor(AppColors.BG_PRIMARY)
          .borderRadius(AppSizes.RADIUS_MD)
        }
      }
      .width('100%')
    }
    .width('100%')
    .padding(AppSizes.SPACING_LG)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(AppSizes.RADIUS_LG)
  }

  /**
   * 复制URL到剪贴板
   */
  private async copyUrl(): Promise<void> {
    if (this.dlnaUri.length === 0) {
      return
    }

    try {
      // 获取系统剪贴板
      const systemPasteboard = pasteboard.getSystemPasteboard()

      // 创建纯文本PasteData对象
      const pasteData: pasteboard.PasteData = pasteboard.createPlainTextData(this.dlnaUri)

      // 设置到剪贴板
      await systemPasteboard.setPasteData(pasteData)

      hilog.info(DOMAIN, TAG, 'URL已复制到剪贴板')

      // 通知回调
      this.onCopy?.()
    } catch (error) {
      hilog.error(DOMAIN, TAG, `复制URL失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 下载视频（触发回调）
   */
  private async downloadVideo(): Promise<void> {
    if (this.dlnaUri.length === 0 || this.isDownloading) {
      return
    }

    this.isDownloading = true
    this.downloadProgress = 0

    try {
      hilog.info(DOMAIN, TAG, `开始下载视频: ${this.dlnaUri.substring(0, 100)}...`)

      // 通知回调开始下载
      this.onDownload?.(this.dlnaUri, this.dlnaHttpHeaders)

      // 实际下载逻辑由Index.ets处理
      // 这里只触发下载事件
    } catch (error) {
      hilog.error(DOMAIN, TAG, `下载失败: ${JSON.stringify(error)}`)
      this.isDownloading = false
    }
  }

  /**
   * 格式化URL用于显示
   */
  private formatUrlForDisplay(url: string): string {
    if (url.length <= 80) {
      return url
    }

    // 截断长URL
    return `${url.substring(0, 40)}...${url.substring(url.length - 37)}`
  }
}
