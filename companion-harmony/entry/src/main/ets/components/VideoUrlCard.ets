/**
 * 视频URL显示卡片组件
 * 显示当前接收到的视频URL，支持复制和下载
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { http } from '@kit.NetworkKit'
import { fileIo } from '@kit.CoreFileKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { pasteboard } from '@kit.BasicServicesKit'

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
      // 标题
      Row() {
        Text('视频地址')
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor('#F1F5F9')

        Blank()

        // URL状态指示
        if (this.dlnaUri.length > 0) {
          Row({ space: 6 }) {
            Circle()
              .width(8)
              .height(8)
              .fill('#10B981')
            Text('已接收')
              .fontSize(12)
              .fontColor('#10B981')
          }
        }
      }
      .width('100%')
      .margin({ bottom: 12 })

      // URL显示区域
      Column() {
        if (this.dlnaUri.length > 0) {
          // 显示URL
          Text(this.formatUrlForDisplay(this.dlnaUri))
            .fontSize(12)
            .fontColor('#94A3B8')
            .width('100%')
            .maxLines(3)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .margin({ bottom: 12 })

          // 按钮区域
          Row({ space: 12 }) {
            // 复制按钮
            Button() {
              Row({ space: 8 }) {
                Text('📋')
                  .fontSize(16)
                Text('复制')
                  .fontSize(14)
                  .fontColor('#F1F5F9')
              }
            }
            .type(ButtonType.Normal)
            .backgroundColor('#334155')
            .borderRadius(8)
            .height(40)
            .padding({ left: 16, right: 16 })
            .onClick(() => {
              this.copyUrl()
            })

            Blank()

            // 下载按钮
            if (this.isDownloading) {
              // 下载中状态
              Row({ space: 8 }) {
                LoadingProgress()
                  .width(16)
                  .height(16)
                  .color('#6366F1')
                Text(`${this.downloadProgress}%`)
                  .fontSize(13)
                  .fontColor('#6366F1')
              }
              .width(100)
              .height(40)
              .padding({ left: 16, right: 16 })
              .backgroundColor('#0F172A')
              .borderRadius(8)
              .justifyContent(FlexAlign.Center)
            } else {
              Button() {
                Row({ space: 8 }) {
                  Text('⬇️')
                    .fontSize(16)
                  Text('下载')
                    .fontSize(14)
                    .fontColor('#F1F5F9')
                }
              }
              .type(ButtonType.Normal)
              .backgroundColor('#6366F1')
              .borderRadius(8)
              .height(40)
              .padding({ left: 16, right: 16 })
              .onClick(() => {
                this.downloadVideo()
              })
            }
          }
          .width('100%')
        } else {
          // 等待接收状态
          Row({ space: 12 }) {
            Circle()
              .width(8)
              .height(8)
              .fill('#64748B')
            Text('等待接收视频地址...')
              .fontSize(13)
              .fontColor('#64748B')
          }
          .width('100%')
          .padding(12)
          .backgroundColor('#0F172A')
          .borderRadius(8)
        }
      }
      .width('100%')
    }
    .width('100%')
    .padding(16)
    .backgroundColor('#1E293B')
    .borderRadius(16)
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
   * 下载视频
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
