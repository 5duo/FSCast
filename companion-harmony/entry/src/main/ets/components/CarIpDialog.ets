/**
 * 车机IP设置对话框（优化版）
 * 使用 HarmonyOS SymbolGlyph 系统图标
 * 支持自动扫描网段功能
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { AppColors, AppSizes } from './StatusPanel'
import { AppConfig } from '../constants/Config'
import { SysIcons } from '../constants/IconResources'
import { NetworkScanService, ScanResult } from '../service/NetworkScanService'

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
        TextInput({ text: this.localIpAddress, placeholder: '例如: 192.168.1.100' })
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
            this.localIpAddress = value.trim()
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

      // 扫描网段区域
      Column() {
        // 区域标题
        Row() {
          SymbolGlyph($r(`sys.symbol.${SysIcons.SEARCH}`))
            .fontSize(AppSizes.ICON_SM)
            .fontColor([AppColors.TEXT_SECONDARY])

          Text('自动扫描')
            .fontSize(13)
            .fontColor(AppColors.TEXT_SECONDARY)
            .margin({ left: AppSizes.SPACING_XS })
        }
        .width('100%')
        .margin({ bottom: AppSizes.SPACING_SM })

        // 扫描按钮
        Button(this.isScanning ? this.scanProgress : '扫描网段')
          .fontSize(13)
          .fontColor(this.isScanning ? AppColors.TEXT_SECONDARY : AppColors.PRIMARY)
          .backgroundColor(AppColors.BG_ELEMENT)
          .borderRadius(AppSizes.RADIUS_MD)
          .width('100%')
          .height(40)
          .enabled(!this.isScanning)
          .onClick(() => {
            this.startNetworkScan()
          })
          .stateEffect(true)

        // 扫描结果
        if (this.scanResults.length > 0) {
          Column({ space: AppSizes.SPACING_XS }) {
            ForEach(this.scanResults, (result: ScanResult, index: number) => {
              Button(`✓ ${result.ip}`)
                .fontSize(12)
                .fontColor(AppColors.PRIMARY)
                .backgroundColor(AppColors.BG_ELEMENT)
                .borderRadius(AppSizes.RADIUS_SM)
                .padding({
                  left: AppSizes.SPACING_SM,
                  right: AppSizes.SPACING_SM,
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
          .margin({ top: AppSizes.SPACING_SM })
        }
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
    if (!AppConfig.isValidIp(this.localIpAddress)) {
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
