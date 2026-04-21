/**
 * 网络工具函数
 * 提供网络相关的实用方法
 */
import { connection } from '@kit.NetworkKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'NetworkUtils'
const DOMAIN = 0x0000

/**
 * 检查是否有WiFi连接
 */
export async function hasWifiConnection(): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    try {
      const netCon = connection.createNetConnection()

      netCon.register((error) => {
        if (error) {
          hilog.error(DOMAIN, TAG, `register netCon failed: ${JSON.stringify(error)}`)
          resolve(false)
          return
        }

        netCon.getProperties((error, netProperties) => {
          if (error || !netProperties) {
            hilog.error(DOMAIN, TAG, `getNetProperties failed: ${JSON.stringify(error)}`)
            resolve(false)
            return
          }

          const hasWifi = netProperties.networkType === connection.NetworkType.WIFI
          hilog.info(DOMAIN, TAG, `WiFi connection: ${hasWifi}`)
          resolve(hasWifi)
        })
      })
    } catch (error) {
      hilog.error(DOMAIN, TAG, `hasWifiConnection error: ${JSON.stringify(error)}`)
      resolve(false)
    }
  })
}

/**
 * 获取本机IP地址
 */
export async function getLocalIpAddress(): Promise<string> {
  try {
    const netCon = connection.createNetConnection()

    return new Promise<string>((resolve) => {
      netCon.register((error) => {
        if (error) {
          hilog.error(DOMAIN, TAG, `register netCon failed: ${JSON.stringify(error)}`)
          resolve('')
          return
        }

        netCon.getProperties((error, netProperties) => {
          if (error || !netProperties) {
            hilog.error(DOMAIN, TAG, `getNetProperties failed: ${JSON.stringify(error)}`)
            resolve('')
            return
          }

          const ipAddress = netProperties.linkAddresses?.[0]?.address?.address || ''
          hilog.info(DOMAIN, TAG, `Local IP: ${ipAddress}`)
          resolve(ipAddress)
        })
      })
    })
  } catch (error) {
    hilog.error(DOMAIN, TAG, `getLocalIpAddress error: ${JSON.stringify(error)}`)
    return ''
  }
}

/**
 * 从IP地址获取网段
 * 例如: 192.168.1.100 -> 192.168.1
 */
export function getNetworkSegment(ipAddress: string): string {
  const parts = ipAddress.split('.')
  if (parts.length === 4) {
    return `${parts[0]}.${parts[1]}.${parts[2]}`
  }
  return ''
}

/**
 * 生成IP地址列表（用于扫描）
 * @param networkSegment 网段，例如 192.168.1
 * @param excludeIp 排除的IP地址（通常是本机IP）
 */
export function generateIpList(networkSegment: string, excludeIp: string = ''): string[] {
  const ipList: string[] = []

  for (let i = 1; i < 255; i++) {
    const ip = `${networkSegment}.${i}`
    if (ip !== excludeIp) {
      ipList.push(ip)
    }
  }

  return ipList
}

/**
 * 验证IP地址格式
 */
export function isValidIpAddress(ip: string): boolean {
  const parts = ip.split('.')
  if (parts.length !== 4) {
    return false
  }

  for (const part of parts) {
    const num = parseInt(part, 10)
    if (isNaN(num) || num < 0 || num > 255) {
      return false
    }
  }

  return true
}

/**
 * 从URL提取文件名
 */
export function extractFileNameFromUrl(url: string): string {
  try {
    const queryIndex = url.indexOf('?')
    const pathOnly = queryIndex >= 0 ? url.substring(0, queryIndex) : url
    const lastSlashIndex = pathOnly.lastIndexOf('/')

    if (lastSlashIndex >= 0 && lastSlashIndex < pathOnly.length - 1) {
      let fileName = pathOnly.substring(lastSlashIndex + 1)

      // URL解码
      try {
        fileName = decodeURIComponent(fileName)
      } catch (e) {
        // 解码失败，使用原始文件名
      }

      // 如果没有扩展名，添加.mp4
      if (!fileName.includes('.')) {
        fileName = `${fileName}.mp4`
      }

      return fileName
    }

    // 默认文件名
    return `video_${Date.now()}.mp4`
  } catch (error) {
    hilog.error(DOMAIN, TAG, `extractFileNameFromUrl error: ${JSON.stringify(error)}`)
    return `video_${Date.now()}.mp4`
  }
}

/**
 * 检查IP地址是否可达（简化版本，仅用于预检查）
 * 注意：实际网络可达性需要通过TCP连接验证
 */
export function isSameNetworkSegment(ip1: string, ip2: string): boolean {
  const segment1 = getNetworkSegment(ip1)
  const segment2 = getNetworkSegment(ip2)
  return segment1 === segment2 && segment1.length > 0
}
