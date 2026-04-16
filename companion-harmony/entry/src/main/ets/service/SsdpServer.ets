/**
 * SSDP设备发现服务
 * 鸿蒙手机端DLNA DMR - SSDP协议实现
 * 用于被DLNA控制点发现
 */
import { socket } from '@kit.NetworkKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0000
const TAG = 'SsdpServer'

export class SsdpServer {
  private static readonly SSDP_ADDRESS = "239.255.255.250"
  private static readonly SSDP_PORT = 1900

  private multicastSocket: socket.MulticastSocket | null = null
  private isRunning: boolean = false
  private notifyTimer: number = -1

  /**
   * 启动SSDP服务
   * 只发送NOTIFY，不监听M-SEARCH，避免与其他应用冲突
   */
  async start(): Promise<void> {
    if (this.isRunning) {
      hilog.warn(DOMAIN, TAG, 'SsdpServer已经在运行')
      return
    }

    try {
      this.isRunning = true

      // 创建多播套接字（只用于发送，不绑定1900端口）
      this.multicastSocket = socket.constructMulticastSocketInstance()

      // 绑定到随机端口（系统自动分配）
      let localAddr: socket.NetAddress = {
        address: "0.0.0.0",
        port: 0,  // 使用0让系统自动分配端口
        family: 1
      }
      await this.multicastSocket.bind(localAddr)

      hilog.info(DOMAIN, TAG, 'SSDP服务启动成功，仅发送NOTIFY模式')

      // 定期发送NOTIFY
      this.startNotifyPeriodically()

    } catch (error) {
      hilog.error(DOMAIN, TAG, `启动SSDP服务失败: ${JSON.stringify(error)}`)
      this.isRunning = false
    }
  }

  /**
   * 监听M-SEARCH请求
   */
  private listenForMSearch(): void {
    if (!this.multicastSocket) {
      return
    }

    this.multicastSocket.on('message', (value: socket.SocketMessageInfo) => {
      if (!this.isRunning) {
        return
      }

      try {
        // 转换ArrayBuffer为字符串
        const message = this.arrayBufferToString(value.message)

        // 响应M-SEARCH请求
        if (message.includes("M-SEARCH")) {
          hilog.debug(DOMAIN, TAG, `收到M-SEARCH请求: ${message.substring(0, 100)}...`)
          this.sendMSearchResponse(value.remoteInfo.address)
        }
      } catch (error) {
        hilog.error(DOMAIN, TAG, `处理M-SEARCH失败: ${JSON.stringify(error)}`)
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
   * 定期发送NOTIFY
   */
  private startNotifyPeriodically(): void {
    // 每5秒发送一次NOTIFY
    this.notifyTimer = setInterval(() => {
      if (this.isRunning) {
        this.sendNotify()
      }
    }, 5000)
  }

  /**
   * 发送NOTIFY消息
   */
  private async sendNotify(): Promise<void> {
    if (!this.multicastSocket) {
      return
    }

    try {
      const deviceUuid = "583f8100-1de2-11db-8981-000c298458a8"

      // 获取本地IP地址
      const localIp = this.getLocalIpAddress()

      const notifyMessage =
`NOTIFY * HTTP/1.1
HOST: ${SsdpServer.SSDP_ADDRESS}:${SsdpServer.SSDP_PORT}
CACHE-CONTROL: max-age=1800
LOCATION: http://${localIp}:49153/description.xml
NT: urn:schemas-upnp-org:device:MediaRenderer:1
NTS: ssdp:alive
SERVER: FSCast Remote/1.0 UPnP/1.0 DLNADOC/1.50
USN: uuid:${deviceUuid}::urn:schemas-upnp-org:device:MediaRenderer:1

`

      const sendOptions: socket.UDPSendOptions = {
        data: notifyMessage,
        address: {
          address: SsdpServer.SSDP_ADDRESS,
          port: SsdpServer.SSDP_PORT
        }
      }
      await this.multicastSocket.send(sendOptions)

      hilog.debug(DOMAIN, TAG, '已发送NOTIFY消息')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `发送NOTIFY失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 发送M-SEARCH响应
   */
  private async sendMSearchResponse(address: string): Promise<void> {
    if (!this.multicastSocket) {
      return
    }

    try {
      const deviceUuid = "583f8100-1de2-11db-8981-000c298458a8"
      const localIp = this.getLocalIpAddress()

      const searchResponse =
`HTTP/1.1 200 OK
CACHE-CONTROL: max-age=1800
EXT:
LOCATION: http://${localIp}:49153/description.xml
SERVER: FSCast Remote/1.0 UPnP/1.0 DLNADOC/1.50
ST: urn:schemas-upnp-org:device:MediaRenderer:1
USN: uuid:${deviceUuid}::urn:schemas-upnp-org:device:MediaRenderer:1

`

      const sendOptions: socket.UDPSendOptions = {
        data: searchResponse,
        address: {
          address: address,
          port: SsdpServer.SSDP_PORT
        }
      }
      await this.multicastSocket.send(sendOptions)

      hilog.info(DOMAIN, TAG, `已发送M-SEARCH响应到: ${address}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `发送M-SEARCH响应失败: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 停止SSDP服务
   */
  async stop(): Promise<void> {
    hilog.info(DOMAIN, TAG, '正在停止SSDP服务...')

    this.isRunning = false

    if (this.notifyTimer !== -1) {
      clearInterval(this.notifyTimer)
      this.notifyTimer = -1
    }

    if (this.multicastSocket) {
      try {
        // 离开多播组
        let multicastAddr: socket.NetAddress = {
          address: SsdpServer.SSDP_ADDRESS,
          port: SsdpServer.SSDP_PORT,
          family: 1
        }
        await this.multicastSocket.dropMembership(multicastAddr)

        this.multicastSocket.off('message')
        await this.multicastSocket.close()
      } catch (error) {
        hilog.error(DOMAIN, TAG, `关闭多播socket失败: ${JSON.stringify(error)}`)
      }
      this.multicastSocket = null
    }

    hilog.info(DOMAIN, TAG, 'SSDP服务已停止')
  }

  /**
   * 获取本地IP地址
   * TODO: 实际应该动态获取WiFi接口的IP地址
   * 目前使用固定IP，用户需要根据实际网络环境修改
   */
  private getLocalIpAddress(): string {
    // 用户手机的实际IP地址
    return "192.168.88.60"
  }
}
