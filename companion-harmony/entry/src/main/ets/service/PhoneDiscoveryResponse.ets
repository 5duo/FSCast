/**
 * 手机设备发现响应服务
 * 鸿蒙手机端 - 响应车机端的UDP设备发现请求
 */
import { socket } from '@kit.NetworkKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0000
const TAG = 'PhoneDiscoveryResponse'

export class PhoneDiscoveryResponse {
  private udpSocket: socket.UDPSocket | null = null
  private isRunning: boolean = false

  // 设备信息
  private readonly deviceName: string
  private readonly devicePlatform: string  // 'harmony' 或 'android'
  private readonly deviceId: string

  constructor(
    deviceName: string = 'FSCast Remote',
    devicePlatform: string = 'harmony'
  ) {
    this.deviceName = deviceName
    this.devicePlatform = devicePlatform
    this.deviceId = `phone_${devicePlatform}_${Date.now()}`
  }

  /**
   * 启动设备发现响应服务
   */
  async start(): Promise<void> {
    if (this.isRunning) {
      hilog.warn(DOMAIN, TAG, 'PhoneDiscoveryResponse已经在运行')
      return
    }

    try {
      this.isRunning = true

      // 创建UDP套接字
      this.udpSocket = socket.constructUDPSocketInstance()

      // 绑定到端口
      let localAddr: socket.NetAddress = {
        address: "0.0.0.0",
        port: 19891
      }
      await this.udpSocket.bind(localAddr)

      hilog.info(DOMAIN, TAG, '设备发现响应服务启动成功，监听端口: 19891')

      // 启动监听
      this.listenForDiscovery()

    } catch (error) {
      hilog.error(DOMAIN, TAG, `启动设备发现响应服务失败: ${JSON.stringify(error)}`)
      this.isRunning = false
    }
  }

  /**
   * 监听车机端的发现请求
   */
  private listenForDiscovery(): void {
    if (!this.udpSocket) {
      return
    }

    this.udpSocket.on('message', (value: socket.SocketMessageInfo) => {
      if (!this.isRunning) {
        return
      }

      try {
        // 转换ArrayBuffer为字符串
        const message = this.arrayBufferToString(value.message)

        // 响应车机端的发现请求
        if (message.includes("FSCAST_PHONE_DISCOVER")) {
          hilog.debug(DOMAIN, TAG, `收到发现请求: ${message}`)
          this.sendDiscoveryResponse(value.remoteInfo.address)
        }
      } catch (error) {
        hilog.error(DOMAIN, TAG, `监听发现请求失败: ${JSON.stringify(error)}`)
      }
    })
  }

  /**
   * ArrayBuffer转字符串
   */
  private arrayBufferToString(buffer: ArrayBuffer): string {
    const dataView = new DataView(buffer)
    let str = ''
    for (let i = 0; i < dataView.byteLength; ++i) {
      str += String.fromCharCode(dataView.getUint8(i))
    }
    return str
  }

  /**
   * 发送发现响应
   */
  private async sendDiscoveryResponse(carAddress: string): Promise<void> {
    if (!this.udpSocket) {
      return
    }

    try {
      const responseMessage = `FSCAST_PHONE_RESPONSE|${this.deviceName}|${this.devicePlatform}|${this.deviceId}`

      const sendOptions: socket.UDPSendOptions = {
        data: responseMessage,
        address: {
          address: carAddress,
          port: 19891
        }
      }
      await this.udpSocket.send(sendOptions)

      hilog.info(DOMAIN, TAG, `已响应发现请求到: ${carAddress}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `发送发现响应失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 停止设备发现响应服务
   */
  async stop(): Promise<void> {
    hilog.info(DOMAIN, TAG, '正在停止设备发现响应服务...')

    this.isRunning = false

    if (this.udpSocket) {
      try {
        this.udpSocket.off('message')
        await this.udpSocket.close()
      } catch (error) {
        hilog.error(DOMAIN, TAG, `关闭UDP socket失败: ${JSON.stringify(error)}`)
      }
      this.udpSocket = null
    }

    hilog.info(DOMAIN, TAG, '设备发现响应服务已停止')
  }
}
