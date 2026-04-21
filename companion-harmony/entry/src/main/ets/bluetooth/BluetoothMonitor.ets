/**
 * 蓝牙状态监听器
 * 通过检查蓝牙配对设备来判断是否有音频设备连接
 */
import { bluetooth } from '@kit.ConnectivityKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'BluetoothMonitor'
const DOMAIN = 0x0000

export class BluetoothMonitor {
  private isMonitoring: boolean = false
  private bluetoothConnected: boolean = false
  private bluetoothDeviceName: string = ''
  private checkTimer: number = -1

  // 回调函数：传递设备名称
  onBluetoothConnected: ((deviceName: string) => void) | null = null
  onBluetoothDisconnected: (() => void) | null = null

  /**
   * 开始监听蓝牙状态
   */
  async startMonitoring(): Promise<void> {
    if (this.isMonitoring) return

    try {
      // 首次检查
      await this.checkBluetoothState()

      // 启动定时检查（每5秒检查一次）
      this.checkTimer = setInterval(async () => {
        await this.checkBluetoothState()
      }, 5000)

      this.isMonitoring = true
      hilog.info(DOMAIN, TAG, 'Bluetooth monitoring started')
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'Start monitoring failed: ' + (error as BusinessError).message)
    }
  }

  /**
   * 检查蓝牙状态
   */
  private async checkBluetoothState(): Promise<void> {
    try {
      // 获取蓝牙状态
      const state = bluetooth.getState()
      hilog.debug(DOMAIN, TAG, `Bluetooth state: ${state}`)

      // STATE_ON = 2, STATE_OFF = 0, STATE_TURNING_ON = 1, STATE_TURNING_OFF = 3
      if (state !== 2) {
        // 蓝牙未开启
        if (this.bluetoothConnected) {
          this.bluetoothConnected = false
          this.bluetoothDeviceName = ''
          this.onBluetoothDisconnected?.()
        }
        return
      }

      // 获取已配对的设备
      const devices: Array<string> = await bluetooth.getPairedDevices()
      hilog.debug(DOMAIN, TAG, `Paaired devices: ${devices.length}`)

      // 检查是否有已配对的设备
      const hasPairedDevices = devices.length > 0
      const stateChanged = this.bluetoothConnected !== hasPairedDevices

      if (stateChanged || (hasPairedDevices && this.bluetoothDeviceName === '')) {
        this.bluetoothConnected = hasPairedDevices

        if (hasPairedDevices) {
          // 获取第一个配对设备的名称
          const deviceId = devices[0]
          try {
            const deviceName = await this.getDeviceName(deviceId)
            this.bluetoothDeviceName = deviceName
            hilog.info(DOMAIN, TAG, `Bluetooth device connected: ${deviceName}`)
            this.onBluetoothConnected?.(deviceName)
          } catch (e) {
            // 如果获取名称失败，使用设备ID
            this.bluetoothDeviceName = deviceId
            hilog.info(DOMAIN, TAG, `Bluetooth device connected: ${deviceId}`)
            this.onBluetoothConnected?.(deviceId)
          }
        } else {
          this.bluetoothDeviceName = ''
          hilog.info(DOMAIN, TAG, 'No bluetooth devices')
          this.onBluetoothDisconnected?.()
        }
      }
    } catch (error) {
      hilog.debug(DOMAIN, TAG, 'Check bluetooth state failed: ' + (error as BusinessError).message)
    }
  }

  /**
   * 获取设备名称
   */
  private async getDeviceName(deviceId: string): Promise<string> {
    try {
      // 使用 getRemoteDeviceName API 获取设备名称
      const deviceName = await bluetooth.getRemoteDeviceName(deviceId)
      if (deviceName && deviceName.length > 0) {
        hilog.info(DOMAIN, TAG, `Got device name: ${deviceName}`)
        return deviceName
      }
      // 如果返回空名称，使用格式化的设备ID
      return this.formatDeviceId(deviceId)
    } catch (error) {
      // 如果获取失败，返回格式化的设备ID
      hilog.debug(DOMAIN, TAG, 'Get device name failed: ' + JSON.stringify(error))
      return this.formatDeviceId(deviceId)
    }
  }

  /**
   * 格式化设备ID为可读格式
   */
  private formatDeviceId(deviceId: string): string {
    // 设备ID格式：XX:XX:XX:XX:XX:XX
    // 只取前3段作为简短标识
    const parts = deviceId.split(':')
    if (parts.length >= 3) {
      return `${parts[0]}:${parts[1]}:${parts[2]}`
    }
    return deviceId.substring(0, 8)
  }

  /**
   * 获取当前蓝牙连接状态
   */
  isBluetoothAudioConnected(): boolean {
    return this.bluetoothConnected
  }

  /**
   * 获取蓝牙设备名称
   */
  getBluetoothDeviceName(): string {
    return this.bluetoothDeviceName
  }

  /**
   * 停止监听
   */
  stopMonitoring(): void {
    if (this.isMonitoring) {
      if (this.checkTimer !== -1) {
        clearInterval(this.checkTimer)
        this.checkTimer = -1
      }
      this.isMonitoring = false
      hilog.info(DOMAIN, TAG, 'Bluetooth monitoring stopped')
    }
  }

  getIsMonitoring(): boolean {
    return this.isMonitoring
  }
}
