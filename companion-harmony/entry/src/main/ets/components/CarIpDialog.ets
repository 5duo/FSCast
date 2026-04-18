/**
 * 车机IP设置对话框（优化版）
 * 使用 HarmonyOS SymbolGlyph 系统图标
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors, AppSizes } from './StatusPanel'
import { AppConfig } from '../constants/Config'
import { SysIcons } from '../constants/IconResources'

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
    Column({ space: AppSizes.SPACING_MD }) {
      // 标题行
      Row() {
        // 标题图标 - 使用系统图标
        SymbolGlyph($r(`sys.symbol.${SysIcons.SETTINGS}`))
          .fontSize(AppSizes.ICON_LG)
          .fontColor([AppColors.PRIMARY])

        // 标题文字
        Text('设置车机IP地址')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(AppColors.TEXT_PRIMARY)
          .margin({ left: AppSizes.SPACING_SM })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      // 说明文字
      Text('请输入车机端的IP地址，确保手机和车机在同一WiFi网络')
        .fontSize(13)
        .fontColor(AppColors.TEXT_SECONDARY)
        .width('100%')

      // IP地址输入框
      Column() {
        TextInput({ text: this.ipAddress, placeholder: '例如: 192.168.1.100' })
          .fontSize(15)
          .fontColor(AppColors.TEXT_PRIMARY)
          .placeholderColor(AppColors.TEXT_DISABLED)
          .backgroundColor(AppColors.BG_PRIMARY)
          .borderColor(
            this.errorMessage.length > 0
              ? AppColors.ERROR
              : AppColors.BG_ELEMENT
          )
          .borderRadius(AppSizes.RADIUS_MD)
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
          Row({ space: AppSizes.SPACING_XS }) {
            SymbolGlyph($r(`sys.symbol.${SysIcons.ERROR}`))
              .fontSize(AppSizes.ICON_SM)
              .fontColor([AppColors.ERROR])

            Text(this.errorMessage)
              .fontSize(12)
              .fontColor(AppColors.ERROR)
          }
          .width('100%')
          .margin({ top: AppSizes.SPACING_XS })
        }
      }
      .width('100%')

      // 快速选择区域
      Column() {
        // 区域标题
        Row() {
          SymbolGlyph($r(`sys.symbol.${SysIcons.LINK}`))
            .fontSize(AppSizes.ICON_SM)
            .fontColor([AppColors.TEXT_SECONDARY])

          Text('常用地址')
            .fontSize(13)
            .fontColor(AppColors.TEXT_SECONDARY)
            .margin({ left: AppSizes.SPACING_XS })
        }
        .width('100%')
        .margin({ bottom: AppSizes.SPACING_SM })

        // 快速选择按钮
        Row({ space: AppSizes.SPACING_SM }) {
          this.QuickIpButton('192.168.1.100')
          this.QuickIpButton(AppConfig.DEFAULTS.CAR_IP)
          this.QuickIpButton('192.168.0.100')
        }
        .width('100%')
      }
      .width('100%')
      .padding(AppSizes.SPACING_MD)
      .backgroundColor(AppColors.BG_PRIMARY)
      .borderRadius(AppSizes.RADIUS_MD)

      // 按钮区域
      Row({ space: AppSizes.SPACING_MD }) {
        // 取消按钮
        Button('取消')
          .fontSize(14)
          .fontColor(AppColors.TEXT_PRIMARY)
          .backgroundColor(AppColors.BG_ELEMENT)
          .borderRadius(AppSizes.RADIUS_MD)
          .layoutWeight(1)
          .height(44)
          .onClick(() => {
            hilog.info(DOMAIN, TAG, '用户取消IP设置')
            this.controller?.close()
          })
          .stateEffect(true)

        // 保存并连接按钮
        Button('保存并连接')
          .fontSize(14)
          .fontColor('#FFFFFF')
          .backgroundColor(AppColors.PRIMARY)
          .borderRadius(AppSizes.RADIUS_MD)
          .layoutWeight(1)
          .height(44)
          .onClick(() => {
            if (this.validateAndSave()) {
              hilog.info(DOMAIN, TAG, `IP地址已保存: ${this.ipAddress}`)
              this.onSave?.(this.ipAddress)
              this.controller?.close()
            }
          })
          .stateEffect(true)
      }
      .width('100%')
    }
    .width(280)
    .padding(AppSizes.SPACING_XL)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(AppSizes.RADIUS_LG)
  }

  /**
   * 快速IP选择按钮
   */
  @Builder QuickIpButton(ip: string) {
    Button(ip)
      .fontSize(12)
      .fontColor(AppColors.TEXT_PRIMARY)
      .backgroundColor(AppColors.BG_ELEMENT)
      .borderRadius(AppSizes.RADIUS_SM)
      .padding({
        left: AppSizes.SPACING_SM,
        right: AppSizes.SPACING_SM,
        top: 6,
        bottom: 6
      })
      .onClick(() => {
        this.ipAddress = ip
        this.errorMessage = ''
        hilog.info(DOMAIN, TAG, `选择快速IP: ${ip}`)
      })
      .stateEffect(true)
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
    if (!AppConfig.isValidIp(this.ipAddress)) {
      this.errorMessage = 'IP地址格式不正确'
      return false
    }

    return true
  }
}
