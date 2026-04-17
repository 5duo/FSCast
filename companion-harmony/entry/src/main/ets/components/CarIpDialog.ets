/**
 * 车机IP设置对话框
 * 用于配置和保存车机端IP地址
 */
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'CarIpDialog'
const DOMAIN = 0x0000

@CustomDialog
export struct CarIpDialog {
  // IP地址
  @Link ipAddress: string
  controller?: CustomDialogController

  // 回调函数
  onSave?: (ip: string) => void

  // 本地状态
  @State errorMessage: string = ''

  build() {
    Column({ space: 16 }) {
      // 标题
      Text('设置车机IP地址')
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor('#F1F5F9')
        .width('100%')

      // 说明文字
      Text('请输入车机端的IP地址，确保手机和车机在同一WiFi网络')
        .fontSize(13)
        .fontColor('#94A3B8')
        .width('100%')

      // IP地址输入框
      Column() {
        TextInput({ text: this.ipAddress, placeholder: '例如: 192.168.1.100' })
          .fontSize(16)
          .fontColor('#F1F5F9')
          .placeholderColor('#64748B')
          .backgroundColor('#0F172A')
          .borderColor('#334155')
          .borderRadius(8)
          .height(48)
          .onChange((value: string) => {
            this.ipAddress = value.trim()
            // 清除错误信息
            if (this.errorMessage.length > 0) {
              this.errorMessage = ''
            }
          })

        // 错误提示
        if (this.errorMessage.length > 0) {
          Text(this.errorMessage)
            .fontSize(12)
            .fontColor('#EF4444')
            .width('100%')
            .margin({ top: 4 })
        }
      }
      .width('100%')

      // 快速选择区域
      Column() {
        Text('常用地址')
          .fontSize(13)
          .fontColor('#94A3B8')
          .width('100%')
          .margin({ bottom: 8 })

        // 快速选择按钮
        Row({ space: 8 }) {
          this.QuickIpButton('192.168.1.100')
          this.QuickIpButton('192.168.200.47')
          this.QuickIpButton('192.168.0.100')
        }
        .width('100%')
      }
      .width('100%')
      .padding(12)
      .backgroundColor('#0F172A')
      .borderRadius(8)

      // 按钮区域
      Row({ space: 12 }) {
        Button('取消')
          .fontSize(14)
          .fontColor('#F1F5F9')
          .backgroundColor('#334155')
          .borderRadius(8)
          .layoutWeight(1)
          .onClick(() => {
            hilog.info(DOMAIN, TAG, '用户取消IP设置')
            this.controller?.close()
          })

        Button('保存并连接')
          .fontSize(14)
          .fontColor('#FFFFFF')
          .backgroundColor('#6366F1')
          .borderRadius(8)
          .layoutWeight(1)
          .onClick(() => {
            if (this.validateAndSave()) {
              hilog.info(DOMAIN, TAG, `IP地址已保存: ${this.ipAddress}`)
              this.onSave?.(this.ipAddress)
              this.controller?.close()
            }
          })
      }
      .width('100%')
    }
    .width('280')
    .padding(20)
    .backgroundColor('#1E293B')
    .borderRadius(16)
  }

  /**
   * 快速IP选择按钮
   */
  @Builder QuickIpButton(ip: string) {
    Button(ip)
      .fontSize(12)
      .fontColor('#F1F5F9')
      .backgroundColor('#334155')
      .borderRadius(6)
      .padding({ left: 8, right: 8, top: 6, bottom: 6 })
      .onClick(() => {
        this.ipAddress = ip
        this.errorMessage = ''
        hilog.info(DOMAIN, TAG, `选择快速IP: ${ip}`)
      })
  }

  /**
   * 验证并保存IP地址
   */
  private validateAndSave(): boolean {
    // 检查是否为空
    if (this.ipAddress.length === 0) {
      this.errorMessage = '请输入IP地址'
      return false
    }

    // 验证IP格式
    if (!this.isValidIp(this.ipAddress)) {
      this.errorMessage = 'IP地址格式不正确'
      return false
    }

    return true
  }

  /**
   * 验证IP地址格式
   */
  private isValidIp(ip: string): boolean {
    const ipPattern = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/
    const match = ip.match(ipPattern)

    if (!match) {
      return false
    }

    // 检查每个段是否在0-255之间
    for (let i = 1; i <= 4; i++) {
      const segment = parseInt(match[i])
      if (segment < 0 || segment > 255) {
        return false
      }
    }

    return true
  }
}
