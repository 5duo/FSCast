/**
 * UDP 发现客户端
 * 广播发现请求，接收车机端响应
 */
import { socket } from '@kit.NetworkKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { UDP_PORT, DISCOVER_MESSAGE, TCP_PORT } from '../common/Constants'
import { DiscoverInfo } from '../model/Models'

const TAG = 'DiscoveryClient'

export class DiscoveryClient {
  private udpSocket: socket.UDPSocket | null = null
  private isRunning: boolean = false

  /**
   * 广播发现请求，返回找到的车机列表
   */
  async discover(timeoutMs: number = 3000): Promise<DiscoverInfo[]> {
    const results: DiscoverInfo[] = []
    this.isRunning = true

    try {
      this.udpSocket = socket.constructUDPSocketInstance()

      // 监听消息
      const messagePromise = new Promise<DiscoverInfo[]>((resolve) => {
        let found: DiscoverInfo[] = []

        this.udpSocket!.on('message', (value: socket.SocketMessageInfo) => {
          try {
            let dataView = new DataView(value.message)
            let str = ''
            for (let i = 0; i < dataView.byteLength; ++i) {
              str += String.fromCharCode(dataView.getUint8(i))
            }
            console.info(TAG, 'UDP received: ' + str)

            let response: Record<string, Object> = JSON.parse(str) as Record<string, Object>
            if (response['type'] === 'discover_response') {
              let info = new DiscoverInfo()
              info.name = (response['name'] as string) || 'FSCast'
              info.ip = (response['ip'] as string) || ''
              info.port = (response['port'] as number) || TCP_PORT
              info.version = (response['version'] as number) || 1
              // 去重
              if (!found.some(item => item.ip === info.ip)) {
                found.push(info)
              }
            }
          } catch (e) {
            console.error(TAG, 'Parse discover response failed: ' + (e as BusinessError).message)
          }
        })

        // 超时后返回结果
        setTimeout(() => {
          this.cleanup()
          resolve(found)
        }, timeoutMs)
      })

      // 绑定本地端口（随机）
      let localAddr: socket.NetAddress = { address: '0.0.0.0', port: 0 }
      await this.udpSocket.bind(localAddr)
      console.info(TAG, 'UDP bound')

      // 启用广播
      await this.udpSocket.setExtraOptions({ broadcast: true } as socket.UDPExtraOptions)

      // 发送广播
      let sendOptions: socket.UDPSendOptions = {
        data: DISCOVER_MESSAGE,
        address: { address: '255.255.255.255', port: UDP_PORT } as socket.NetAddress
      }
      await this.udpSocket.send(sendOptions)
      console.info(TAG, 'Discover broadcast sent')

      // 等待响应
      return await messagePromise

    } catch (error) {
      console.error(TAG, 'Discover failed: ' + (error as BusinessError).message)
      this.cleanup()
      return results
    }
  }

  /**
   * 停止发现
   */
  stop(): void {
    this.isRunning = false
    this.cleanup()
  }

  private cleanup(): void {
    if (this.udpSocket) {
      try {
        this.udpSocket.off('message')
        this.udpSocket.close()
      } catch (e) {
        // 忽略关闭错误
      }
      this.udpSocket = null
    }
  }
}
