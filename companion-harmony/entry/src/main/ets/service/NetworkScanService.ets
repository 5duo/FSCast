/**
 * 网络扫描服务
 * 根据手机IP自动扫描同一网段内的车机端
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import { http } from '@kit.NetworkKit'
import { connection } from '@kit.NetworkKit'

const TAG = 'NetworkScanService'
const DOMAIN = 0x0000
const CAR_PORT = 9999  // 车机端WebSocket服务端口
const SCAN_TIMEOUT = 500  // 每个IP扫描超时时间（毫秒）- 缩短以加快扫描速度

export class ScanResult {
  ip: string = ''
  isCarServer: boolean = false
  latency?: number  // 延迟（毫秒）
}

export interface ScanCallback {
  onProgress?: (current: number, total: number, currentIp: string) => void
  onFound?: (result: ScanResult) => void
  onComplete?: (results: ScanResult[]) => void
  onError?: (error: string) => void
}

// 网段范围类型
class NetworkRange {
  start: string = ''
  end: string = ''
}

export class NetworkScanService {
  private isScanning: boolean = false
  private shouldStop: boolean = false
  private readonly CONCURRENT_SIZE: number = 50  // 并发扫描数量 - 增加以加快扫描速度

  /**
   * 检查是否有WiFi连接
   * @returns 是否连接到WiFi
   */
  async hasWifiConnection(): Promise<boolean> {
    try {
      const netHandle = await connection.getDefaultNet()
      if (!netHandle) {
        hilog.warn(DOMAIN, TAG, '无法获取默认网络句柄')
        return false
      }

      const netProperties = await connection.getConnectionProperties(netHandle)
      if (!netProperties) {
        hilog.warn(DOMAIN, TAG, '无法获取网络连接属性')
        return false
      }

      // 检查是否有有效的IP地址
      const linkAddresses = netProperties.linkAddresses
      if (linkAddresses && linkAddresses.length > 0) {
        // 检查是否有IPv4地址
        for (let i = 0; i < linkAddresses.length; i++) {
          const ipAddress = linkAddresses[i].address.address
          // 有IPv4地址说明有网络连接
          if (ipAddress && ipAddress.indexOf(':') === -1 && ipAddress.length > 0) {
            hilog.info(DOMAIN, TAG, `检测到网络连接: ${ipAddress}`)
            return true
          }
        }
      }

      hilog.warn(DOMAIN, TAG, '未检测到有效的网络连接')
      return false
    } catch (error) {
      hilog.error(DOMAIN, TAG, `检查WiFi连接失败: ${JSON.stringify(error)}`)
      return false
    }
  }

  /**
   * 获取手机本机IP地址
   * @returns 手机IP地址，如果获取失败返回空字符串
   */
  async getLocalIpAddress(): Promise<string> {
    try {
      hilog.info(DOMAIN, TAG, '开始获取本机IP地址')

      // 获取默认网络
      const netHandle = await connection.getDefaultNet()
      hilog.info(DOMAIN, TAG, `获取到默认网络句柄: ${JSON.stringify(netHandle)}`)

      if (!netHandle) {
        hilog.error(DOMAIN, TAG, '无法获取默认网络句柄')
        return ''
      }

      // 获取网络连接属性
      const netProperties = await connection.getConnectionProperties(netHandle)
      hilog.info(DOMAIN, TAG, `获取到网络连接属性: ${JSON.stringify(netProperties)}`)

      if (!netProperties) {
        hilog.error(DOMAIN, TAG, '无法获取网络连接属性')
        return ''
      }

      // 从连接属性中提取IP地址
      const linkAddresses = netProperties.linkAddresses
      hilog.info(DOMAIN, TAG, `linkAddresses数量: ${linkAddresses?.length || 0}`)

      if (linkAddresses && linkAddresses.length > 0) {
        // 遍历所有地址，找到IPv4地址
        for (let i = 0; i < linkAddresses.length; i++) {
          const ipAddress = linkAddresses[i].address.address
          hilog.info(DOMAIN, TAG, `地址${i}: ${ipAddress}`)

          // 只返回IPv4地址（不包含冒号的）
          if (ipAddress && ipAddress.indexOf(':') === -1 && ipAddress.length > 0) {
            hilog.info(DOMAIN, TAG, `✓ 获取到本机IPv4: ${ipAddress}`)
            return ipAddress
          }
        }
      }

      hilog.error(DOMAIN, TAG, '未找到有效的IPv4地址')
      return ''
    } catch (error) {
      hilog.error(DOMAIN, TAG, `获取本机IP失败: ${JSON.stringify(error)}`)
      return ''
    }
  }

  /**
   * 根据手机IP获取网段范围
   * @param phoneIp 手机IP地址（如：192.168.95.60）
   * @returns 网段起始IP和结束IP
   */
  private getNetworkRange(phoneIp: string): NetworkRange {
    const result = new NetworkRange()

    try {
      const parts = phoneIp.split('.')
      if (parts.length !== 4) {
        throw new Error('无效的IP地址格式')
      }

      const networkBase = `${parts[0]}.${parts[1]}.${parts[2]}`
      hilog.info(DOMAIN, TAG, `网段: ${networkBase}.x`)

      result.start = `${networkBase}.1`
      result.end = `${networkBase}.254`
      return result
    } catch (error) {
      hilog.error(DOMAIN, TAG, `获取网段范围失败`)
      if (error instanceof Error) {
        throw error
      } else {
        throw new Error('获取网段范围失败')
      }
    }
  }

  /**
   * 检查单个IP是否是车机端
   * @param ip IP地址
   * @returns 是否是车机端
   */
  private async checkIp(ip: string): Promise<boolean> {
    try {
      const url = `http://${ip}:${CAR_PORT}`
      const startTime = Date.now()

      hilog.debug(DOMAIN, TAG, `检查IP: ${ip}`)

      // 尝试连接到车机端的WebSocket端口
      // 使用GET请求检测服务是否运行
      const httpRequest = http.createHttp()

      const response = await httpRequest.request(
        url,
        {
          method: http.RequestMethod.GET,
          header: {
            'Content-Type': 'application/json'
          },
          connectTimeout: SCAN_TIMEOUT,
          readTimeout: SCAN_TIMEOUT
        }
      )

      const latency = Date.now() - startTime
      const isCarServer = response.responseCode === 200 ||
                          response.responseCode === 404 ||  // 404也说明服务器在运行
                          response.responseCode === 426  // WebSocket升级请求

      if (isCarServer) {
        hilog.info(DOMAIN, TAG, `✓ 找到车机端: ${ip} (延迟: ${latency}ms)`)
      }

      return isCarServer
    } catch (error) {
      // 连接失败，不是车机端
      return false
    }
  }

  /**
   * 扫描网段
   * @param phoneIp 手机IP地址
   * @param callback 扫描回调
   */
  async scanNetwork(phoneIp: string, callback: ScanCallback): Promise<void> {
    if (this.isScanning) {
      hilog.warn(DOMAIN, TAG, '已有扫描任务在运行')
      callback.onError?.('扫描任务已在运行')
      return
    }

    this.isScanning = true
    this.shouldStop = false

    try {
      const range = this.getNetworkRange(phoneIp)
      const startIp = range.start
      const endIp = range.end

      // 计算扫描的IP总数
      const startParts = startIp.split('.')
      const endParts = endIp.split('.')
      const totalIps: number = (parseInt(endParts[3]) - parseInt(startParts[3]) + 1)

      hilog.info(DOMAIN, TAG, `开始扫描网段: ${startIp} - ${endIp}`)
      hilog.info(DOMAIN, TAG, `总共 ${totalIps} 个IP地址，并发数: ${this.CONCURRENT_SIZE}`)

      const results: ScanResult[] = []
      let checkedCount = 0

      // 生成所有IP列表
      const ipList: string[] = []
      for (let i = parseInt(startParts[3]); i <= parseInt(endParts[3]); i++) {
        ipList.push(`${startParts[0]}.${startParts[1]}.${startParts[2]}.${i}`)
      }

      // 分批并发扫描
      for (let batchStart = 0; batchStart < ipList.length; batchStart += this.CONCURRENT_SIZE) {
        if (this.shouldStop) {
          hilog.info(DOMAIN, TAG, '扫描已取消')
          break
        }

        const batchEnd = Math.min(batchStart + this.CONCURRENT_SIZE, ipList.length)
        const currentBatch = ipList.slice(batchStart, batchEnd)

        // 并发扫描当前批次
        const batchResults = await Promise.all(
          currentBatch.map(async (ip) => {
            const isCarServer = await this.checkIp(ip)
            if (isCarServer) {
              const result = new ScanResult()
              result.ip = ip
              result.isCarServer = true
              return result
            }
            return null
          })
        )

        // 处理批次结果
        for (const result of batchResults) {
          if (result !== null) {
            results.push(result)
            callback.onFound?.(result)
          }
        }

        // 更新进度
        checkedCount = batchEnd
        callback.onProgress?.(checkedCount, totalIps, currentBatch[currentBatch.length - 1])
      }

      hilog.info(DOMAIN, TAG, `扫描完成，找到 ${results.length} 个车机端`)
      callback.onComplete?.(results)

    } catch (error) {
      hilog.error(DOMAIN, TAG, `扫描失败`)
      callback.onError?.('扫描失败，请检查网络连接')
    } finally {
      this.isScanning = false
      this.shouldStop = false
    }
  }

  /**
   * 取消扫描
   */
  cancelScan(): void {
    if (this.isScanning) {
      hilog.info(DOMAIN, TAG, '取消扫描')
      this.shouldStop = true
    }
  }

  /**
   * 检查是否正在扫描
   */
  isActive(): boolean {
    return this.isScanning
  }
}
