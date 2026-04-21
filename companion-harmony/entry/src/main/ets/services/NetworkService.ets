/**
 * 网络服务（整合版）
 * 整合网络扫描、网络监听、IP地址获取等功能
 */
import { connection } from '@kit.NetworkKit'
import { socket } from '@kit.NetworkKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { Logger, hasWifiConnection, getLocalIpAddress, generateIpList } from '../utils/NetworkUtils'

const TAG = 'NetworkService'
const DOMAIN = 0x0000
const logger = new Logger(TAG, DOMAIN)

/**
 * 扫描结果接口
 */
export interface ScanResult {
  ip: string
  port: number
  responseTime: number
}

/**
 * 扫描回调接口
 */
export interface ScanCallbacks {
  onProgress?: (current: number, total: number, currentIp: string) => void
  onFound?: (result: ScanResult) => void
  onComplete?: (results: ScanResult[]) => void
  onError?: (error: string) => void
}

/**
 * 网络状态回调
 */
export interface NetworkCallbacks {
  onNetworkAvailable?: () => void
  onNetworkLost?: () => void
}

export class NetworkService {
  private netCon: connection.NetConnection | null = null
  private isMonitoring: boolean = false
  private networkCallbacks: NetworkCallbacks = {}

  constructor() {
    logger.info('NetworkService initialized')
  }

  /**
   * 设置网络状态回调
   */
  setNetworkCallbacks(callbacks: NetworkCallbacks): void {
    this.networkCallbacks = { ...this.networkCallbacks, ...callbacks }
  }

  /**
   * 检查是否有WiFi连接
   */
  async hasWifiConnection(): Promise<boolean> {
    return hasWifiConnection()
  }

  /**
   * 获取本机IP地址
   */
  async getLocalIpAddress(): Promise<string> {
    return getLocalIpAddress()
  }

  /**
   * 设置网络状态监听
   */
  setupNetworkListener(): void {
    if (this.isMonitoring) {
      logger.warn('Network monitoring already active')
      return
    }

    try {
      this.netCon = connection.createNetConnection()

      // 网络可用
      this.netCon.on('netAvailable', () => {
        logger.infoWithDivider('Network available')
        this.networkCallbacks.onNetworkAvailable?.()
      })

      // 网络丢失
      this.netCon.on('netLost', () => {
        logger.warnWithDivider('Network lost')
        this.networkCallbacks.onNetworkLost?.()
      })

      // 注册监听
      this.netCon.register((error) => {
        if (error) {
          logger.error(`Register netCon failed: ${JSON.stringify(error)}`)
          return
        }
        this.isMonitoring = true
        logger.info('Network listener started')
      })

    } catch (error) {
      logger.error(`Setup network listener failed: ${JSON.stringify(error)}`)
    }
  }

  /**
   * 停止网络监听
   */
  stopNetworkListener(): void {
    if (this.netCon) {
      this.netCon.unregister((error) => {
        if (error) {
          logger.error(`Unregister netCon failed: ${JSON.stringify(error)}`)
          return
        }
        this.isMonitoring = false
        this.netCon = null
        logger.info('Network listener stopped')
      })
    }
  }

  /**
   * 扫描网络查找车机端
   * @param phoneIp 本机IP地址
   * @param callbacks 扫描回调
   */
  async scanNetwork(phoneIp: string, callbacks: ScanCallbacks): Promise<void> {
    try {
      logger.infoWithDivider(`Starting network scan from ${phoneIp}`)

      // 1. 检查WiFi连接
      const hasWifi = await this.hasWifiConnection()
      if (!hasWifi) {
        callbacks.onError?.('No WiFi connection')
        return
      }

      // 2. 获取网段
      const parts = phoneIp.split('.')
      if (parts.length !== 4) {
        callbacks.onError?.('Invalid IP address format')
        return
      }

      const networkSegment = `${parts[0]}.${parts[1]}.${parts[2]}`
      logger.info(`Network segment: ${networkSegment}`)

      // 3. 生成IP列表（排除本机IP）
      const ipList = generateIpList(networkSegment, phoneIp)
      logger.info(`Generated ${ipList.length} IPs to scan`)

      // 4. 开始扫描
      const results: ScanResult[] = []
      const port = 9999  // FSCast默认端口
      const timeout = 500  // 500ms超时

      let completed = 0
      const total = ipList.length

      // 并发扫描（最多同时扫描50个）
      const batchSize = 50
      for (let i = 0; i < ipList.length; i += batchSize) {
        const batch = ipList.slice(i, i + batchSize)
        await Promise.all(batch.map(ip => this.checkServer(ip, port, timeout).then(result => {
          completed++
          callbacks.onProgress?.(completed, total, ip)

          if (result) {
            results.push(result)
            callbacks.onFound?.(result)
            logger.info(`Found server: ${ip}:${port} (${result.responseTime}ms)`)
          }
        })))
      }

      logger.info(`Scan completed: found ${results.length} servers`)
      callbacks.onComplete?.(results)

    } catch (error) {
      logger.error(`Scan network failed: ${JSON.stringify(error)}`)
      callbacks.onError?.((error as Error).message)
    }
  }

  /**
   * 检查指定IP和端口是否有服务器响应
   */
  private async checkServer(
    ip: string,
    port: number,
    timeout: number
  ): Promise<ScanResult | null> {
    return new Promise<ScanResult | null>((resolve) => {
      let tcpSocket: socket.TCPSocket | null = null
      let timer: number = -1

      const cleanup = () => {
        if (timer !== -1) {
          clearTimeout(timer)
        }
        if (tcpSocket) {
          try {
            tcpSocket.off('connect')
            tcpSocket.off('error')
            tcpSocket.close()
          } catch (e) {
            // 忽略清理错误
          }
        }
      }

      try {
        const startTime = Date.now()
        tcpSocket = socket.constructTCPSocketInstance()

        tcpSocket.on('connect', () => {
          const responseTime = Date.now() - startTime
          cleanup()
          resolve({ ip, port, responseTime })
        })

        tcpSocket.on('error', () => {
          cleanup()
          resolve(null)
        })

        const connectOptions: socket.TCPConnectOptions = {
          address: { address: ip, port: port } as socket.NetAddress,
          timeout: timeout
        }

        tcpSocket.connect(connectOptions)

        // 设置超时
        timer = setTimeout(() => {
          cleanup()
          resolve(null)
        }, timeout)

      } catch (error) {
        cleanup()
        resolve(null)
      }
    })
  }

  /**
   * 是否正在监听网络状态
   */
  isMonitoringNetwork(): boolean {
    return this.isMonitoring
  }

  /**
   * 释放资源
   */
  release(): void {
    this.stopNetworkListener()
    logger.info('NetworkService released')
  }
}
