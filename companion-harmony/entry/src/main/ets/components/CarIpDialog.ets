/**
 * 车机IP设置对话框（优化版）
 * 使用 HarmonyOS SymbolGlyph 系统图标
 * 支持自动扫描网段功能
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors } from '../constants/AppColors'
import { NetworkScanService, ScanResult } from '../service/NetworkScanService'

// 内联尺寸常量
const SPACING_SM = 8
const SPACING_MD = 12
const SPACING_LG = 16
const SPACING_XL = 24
const RADIUS_MD = 8
const RADIUS_LG = 12

// IP验证函数
function isValidIp(ip: string): boolean {
  const parts = ip.split('.')
  if (parts.length !== 4) return false
  for (const part of parts) {
    const num = parseInt(part, 10)
    if (isNaN(num) || num < 0 || num > 255) return false
  }
  return true
}

const TAG = 'CarIpDialog'
const DOMAIN = 0x0000

@CustomDialog
export struct CarIpDialog {
  // IP地址初始值
  ipAddress: string = ''
  controller?: CustomDialogController

  // 回调函数
  onSave?: (ip: string) => void

  // 本地状态
  @State localIpAddress: string = ''  // 本地输入状态
  @State errorMessage: string = ''
  @State isScanning: boolean = false
  @State scanProgress: string = ''
  @State scanResults: ScanResult[] = []

  // 网络扫描服务
  private networkScanService: NetworkScanService = new NetworkScanService()

  aboutToAppear(): void {
    // 初始化本地IP地址
    this.localIpAddress = this.ipAddress
  }

  build() {
    Column({ space: 12 }) {
      // 标题行
      Row() {
        // 标题图标 - 使用系统图标
        Text("⚙")
          .fontSize(24)
          .fontColor(AppColors.PRIMARY)

        // 标题文字
        Text('设置车机IP地址')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(AppColors.TEXT_PRIMARY)
          .margin({ left: 8 })
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
        TextInput({ text: this.localIpAddress, placeholder: '例如: 192.168.1.100' })
          .fontSize(15)
          .fontColor(AppColors.TEXT_PRIMARY)
          .placeholderColor(AppColors.TEXT_DISABLED)
          .backgroundColor(AppColors.BG_PRIMARY)
          .borderColor(
            this.errorMessage.length > 0
              ? AppColors.STATUS_WARNING
              : AppColors.BG_ELEMENT
          )
          .borderRadius(8)
          .height(48)
          .onChange((value: string) => {
            this.localIpAddress = value.trim()
            // 清除错误信息
            if (this.errorMessage.length > 0) {
              this.errorMessage = ''
            }
          })

        // 错误提示
        if (this.errorMessage.length > 0) {
          Row({ space: 4 }) {
            Text('⚠')
              .fontSize(16)
              .fontColor(AppColors.STATUS_WARNING)

            Text(this.errorMessage)
              .fontSize(12)
              .fontColor(AppColors.STATUS_WARNING)
          }
          .width('100%')
          .margin({ top: 4 })
        }
      }
      .width('100%')

      // 快速选择区域
      Column() {
        // 区域标题
        Row() {
          Text("🔗").fontSize(16).fontColor(AppColors.TEXT_SECONDARY)
            .fontSize(16)
            .fontColor(AppColors.TEXT_SECONDARY)

          Text('常用地址')
            .fontSize(13)
            .fontColor(AppColors.TEXT_SECONDARY)
            .margin({ left: 4 })
        }
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
      .backgroundColor(AppColors.BG_PRIMARY)
      .borderRadius(8)

      // 扫描网段区域
      Column() {
        // 区域标题
        Row() {
          Text("🔍").fontSize(16).fontColor(AppColors.TEXT_SECONDARY)
            .fontSize(16)
            .fontColor(AppColors.TEXT_SECONDARY)

          Text('自动扫描')
            .fontSize(13)
            .fontColor(AppColors.TEXT_SECONDARY)
            .margin({ left: 4 })
        }
        .width('100%')
        .margin({ bottom: 8 })

        // 扫描按钮
        Button(this.isScanning ? this.scanProgress : '扫描网段')
          .fontSize(13)
          .fontColor(this.isScanning ? AppColors.TEXT_SECONDARY : AppColors.PRIMARY)
          .backgroundColor(AppColors.BG_ELEMENT)
          .borderRadius(8)
          .width('100%')
          .height(40)
          .enabled(!this.isScanning)
          .onClick(() => {
            this.startNetworkScan()
          })
          .stateEffect(true)

        // 扫描结果
        if (this.scanResults.length > 0) {
          Column({ space: 4 }) {
            ForEach(this.scanResults, (result: ScanResult, index: number) => {
              Button(`✓ ${result.ip}`)
                .fontSize(12)
                .fontColor(AppColors.PRIMARY)
                .backgroundColor(AppColors.BG_ELEMENT)
                .borderRadius(4)
                .padding({
                  left: 8,
                  right: 8,
                  top: 6,
                  bottom: 6
                })
                .onClick(() => {
                  this.localIpAddress = result.ip
                  this.errorMessage = ''
                  hilog.info(DOMAIN, TAG, `选择扫描到的IP: ${result.ip}`)
                })
                .stateEffect(true)
            })
          }
          .width('100%')
          .margin({ top: 8 })
        }
      }
      .width('100%')
      .padding(12)
      .backgroundColor(AppColors.BG_PRIMARY)
      .borderRadius(8)

      // 按钮区域
      Row({ space: 12 }) {
        // 取消按钮
        Button('取消')
          .fontSize(14)
          .fontColor(AppColors.TEXT_PRIMARY)
          .backgroundColor(AppColors.BG_ELEMENT)
          .borderRadius(8)
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
          .borderRadius(8)
          .layoutWeight(1)
          .height(44)
          .onClick(() => {
            if (this.validateAndSave()) {
              hilog.info(DOMAIN, TAG, `IP地址已保存: ${this.localIpAddress}`)
              this.onSave?.(this.localIpAddress)
              this.controller?.close()
            }
          })
          .stateEffect(true)
      }
      .width('100%')
    }
    .width(280)
    .padding(24)
    .backgroundColor(AppColors.BG_CARD)
    .borderRadius(12)
  }

  /**
   * 快速IP选择按钮
   */
  @Builder QuickIpButton(ip: string) {
    Button(ip)
      .fontSize(12)
      .fontColor(AppColors.TEXT_PRIMARY)
      .backgroundColor(AppColors.BG_ELEMENT)
      .borderRadius(4)
      .padding({
        left: 8,
        right: 8,
        top: 6,
        bottom: 6
      })
      .onClick(() => {
        this.localIpAddress = ip
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
    if (this.localIpAddress.length === 0) {
      this.errorMessage = '请输入IP地址'
      return false
    }

    // 验证IP格式
    if (!isValidIp(this.localIpAddress)) {
      this.errorMessage = 'IP地址格式不正确'
      return false
    }

    return true
  }

  /**
   * 开始扫描网段
   */
  private async startNetworkScan(): Promise<void> {
    hilog.info(DOMAIN, TAG, '开始扫描网段')

    // 先获取手机本机IP
    this.isScanning = true
    this.scanProgress = '正在获取本机IP...'
    this.scanResults = []

    const phoneIp = await this.networkScanService.getLocalIpAddress()

    if (!phoneIp || phoneIp.length === 0) {
      this.isScanning = false
      this.scanProgress = '获取本机IP失败，请检查网络连接'
      this.errorMessage = '无法获取本机IP地址'

      // 3秒后清除进度信息
      setTimeout(() => {
        this.scanProgress = ''
      }, 3000)
      return
    }

    hilog.info(DOMAIN, TAG, `本机IP: ${phoneIp}`)
    this.scanProgress = '正在扫描网段...'

    this.networkScanService.scanNetwork(phoneIp, {
      onProgress: (current: number, total: number, currentIp: string) => {
        this.scanProgress = `扫描中: ${current}/${total} (${currentIp})`
        hilog.debug(DOMAIN, TAG, this.scanProgress)
      },
      onFound: (result: ScanResult) => {
        hilog.info(DOMAIN, TAG, `找到设备: ${result.ip}`)
        // 创建新数组，添加找到的结果
        this.scanResults = [...this.scanResults, result]
      },
      onComplete: (results: ScanResult[]) => {
        this.isScanning = false
        this.scanProgress = `扫描完成，找到 ${results.length} 个设备`

        if (results.length > 0) {
          // 自动选择第一个找到的车机端
          this.localIpAddress = results[0].ip
          this.errorMessage = ''
          hilog.info(DOMAIN, TAG, `自动选择第一个车机端: ${results[0].ip}`)
        } else {
          this.scanProgress = '扫描完成，未找到车机端'
        }

        // 3秒后清除进度信息
        setTimeout(() => {
          this.scanProgress = ''
        }, 3000)
      },
      onError: (error: string) => {
        this.isScanning = false
        this.scanProgress = `扫描失败: ${error}`
        hilog.error(DOMAIN, TAG, this.scanProgress)
      }
    })
  }
}
