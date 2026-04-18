/**
 * 应用配置常量
 * 统一管理所有配置项，消除硬编码
 */

// ==================== 类型定义 ====================

interface WebSocketConfig {
  PORT: number
  TIMEOUT: number
  HEARTBEAT_INTERVAL: number
  RECONNECT_INTERVAL: number
  MAX_RECONNECT_ATTEMPTS: number
}

interface AudioStreamConfig {
  PORT: number
  SAMPLE_RATE: number
  CHANNELS: number
  BITS_PER_SAMPLE: number
  BUFFER_SIZE: number
  PREBUFFER_DURATION: number
}

interface DlnaConfig {
  HTTP_PORT: number
  SSDP_PORT: number
  SSDP_INTERVAL: number
  DEVICE_TYPE: string
}

interface DiscoveryConfig {
  UDP_PORT: number
  TIMEOUT: number
  BROADCAST_INTERVAL: number
}

interface SyncConfig {
  INTERVAL: number
  THRESHOLD: number
  STATE_UPDATE_INTERVAL: number
}

interface DefaultsConfig {
  CAR_IP: string
  VOLUME: number
  PLAYBACK_SPEED: number
}

interface VideoConfig {
  PREFERRED_WIDTH: number
  PREFERRED_HEIGHT: number
  BUFFER_DURATION: number
  MAX_RETRIES: number
}

interface BackgroundConfig {
  NOTIFICATION_ID: number
  MIN_BUFFER_FOR_BG: number
}

interface UiConfig {
  STATUS_CHECK_INTERVAL: number
  TOAST_DURATION: number
  DIALOG_ANIMATION_DURATION: number
}

interface LogConfig {
  DOMAIN: number
  MAX_LOG_SIZE: number
}

interface PerformanceConfig {
  MAX_CONCURRENT_DOWNLOADS: number
  DOWNLOAD_CHUNK_SIZE: number
  IMAGE_CACHE_SIZE: number
}

interface NetworkConfig {
  CONNECT_TIMEOUT: number
  READ_TIMEOUT: number
  MAX_REDIRECTS: number
}

interface AppConfigInfo {
  NAME: string
  VERSION: string
  VERSION_CODE: number
  AUTHOR: string
}

// ==================== 配置类 ====================

export class AppConfig {
  // ==================== WebSocket 配置 ====================
  static readonly WS: WebSocketConfig = {
    PORT: 9999,                    // WebSocket 端口
    TIMEOUT: 10000,                // 连接超时时间（毫秒）
    HEARTBEAT_INTERVAL: 30000,     // 心跳间隔（毫秒）
    RECONNECT_INTERVAL: 3000,      // 重连间隔（毫秒）
    MAX_RECONNECT_ATTEMPTS: 5,     // 最大重连次数
  }

  // ==================== 音频流配置 ====================
  static readonly AUDIO_STREAM: AudioStreamConfig = {
    PORT: 9998,                    // 音频流端口
    SAMPLE_RATE: 44100,            // 采样率
    CHANNELS: 2,                   // 声道数（2 = 立体声）
    BITS_PER_SAMPLE: 16,           // 每样本位数
    BUFFER_SIZE: 4096,             // 缓冲区大小
    PREBUFFER_DURATION: 1500,      // 预缓冲时长（毫秒）
  }

  // ==================== DLNA 配置 ====================
  static readonly DLNA: DlnaConfig = {
    HTTP_PORT: 8080,               // DLNA HTTP 端口
    SSDP_PORT: 1900,               // SSDP 端口
    SSDP_INTERVAL: 5000,           // SSDP 广播间隔（毫秒）
    DEVICE_TYPE: 'urn:schemas-upnp-org:device:MediaRenderer:1',
  }

  // ==================== 设备发现配置 ====================
  static readonly DISCOVERY: DiscoveryConfig = {
    UDP_PORT: 19891,               // UDP 发现端口
    TIMEOUT: 3000,                 // 发现超时时间（毫秒）
    BROADCAST_INTERVAL: 2000,      // 广播间隔（毫秒）
  }

  // ==================== 同步配置 ====================
  static readonly SYNC: SyncConfig = {
    INTERVAL: 10000,               // 进度同步间隔（毫秒）
    THRESHOLD: 3000,               // 同步阈值（毫秒）- 差异超过此值才同步
    STATE_UPDATE_INTERVAL: 1000,   // 状态更新间隔（毫秒）
  }

  // ==================== 默认值 ====================
  static readonly DEFAULTS: DefaultsConfig = {
    CAR_IP: '192.168.200.47',      // 默认车机 IP 地址
    VOLUME: 1.0,                   // 默认音量（0.0 - 1.0）
    PLAYBACK_SPEED: 1.0,           // 默认播放速度
  }

  // ==================== 视频播放配置 ====================
  static readonly VIDEO: VideoConfig = {
    PREFERRED_WIDTH: 1920,         // 首选宽度
    PREFERRED_HEIGHT: 1080,        // 首选高度
    BUFFER_DURATION: 5,            // 缓冲时长（秒）
    MAX_RETRIES: 3,                // 最大重试次数
  }

  // ==================== 后台播放配置 ====================
  static readonly BACKGROUND: BackgroundConfig = {
    NOTIFICATION_ID: 1002,         // 通知 ID
    MIN_BUFFER_FOR_BG: 3000,       // 后台播放最小缓冲（毫秒）
  }

  // ==================== UI 配置 ====================
  static readonly UI: UiConfig = {
    STATUS_CHECK_INTERVAL: 5000,   // 状态检查间隔（毫秒）
    TOAST_DURATION: 2000,          // Toast 显示时长（毫秒）
    DIALOG_ANIMATION_DURATION: 300,// 对话框动画时长（毫秒）
  }

  // ==================== 日志配置 ====================
  static readonly LOG: LogConfig = {
    DOMAIN: 0x0000,                // 日志域
    MAX_LOG_SIZE: 1024 * 1024,     // 最大日志大小（字节）
  }

  // ==================== 性能配置 ====================
  static readonly PERFORMANCE: PerformanceConfig = {
    MAX_CONCURRENT_DOWNLOADS: 3,   // 最大并发下载数
    DOWNLOAD_CHUNK_SIZE: 8192,     // 下载块大小（字节）
    IMAGE_CACHE_SIZE: 50 * 1024 * 1024, // 图片缓存大小（字节）
  }

  // ==================== 网络配置 ====================
  static readonly NETWORK: NetworkConfig = {
    CONNECT_TIMEOUT: 30000,        // 连接超时（毫秒）
    READ_TIMEOUT: 60000,           // 读取超时（毫秒）
    MAX_REDIRECTS: 5,              // 最大重定向次数
  }

  // ==================== 应用信息 ====================
  static readonly APP: AppConfigInfo = {
    NAME: 'FSCast Remote',
    VERSION: '1.0.0',
    VERSION_CODE: 1000000,
    AUTHOR: 'FSCast Team',
  }

  // ==================== 辅助方法 ====================

  /**
   * 获取完整的 WebSocket URL
   */
  static getWebSocketUrl(ip: string): string {
    return `ws://${ip}:${AppConfig.WS.PORT}`
  }

  /**
   * 获取完整的 HTTP URL
   */
  static getHttpUrl(ip: string, path: string = ''): string {
    return `http://${ip}:${AppConfig.DLNA.HTTP_PORT}${path}`
  }

  /**
   * 验证 IP 地址格式
   */
  static isValidIp(ip: string): boolean {
    const ipPattern = /^(\d{1,3}\.){3}\d{1,3}$/
    if (!ipPattern.test(ip)) {
      return false
    }

    const parts = ip.split('.')
    return parts.every(part => {
      const num = parseInt(part, 10)
      return num >= 0 && num <= 255
    })
  }

  /**
   * 格式化时间（毫秒 -> 分:秒）
   */
  static formatTime(milliseconds: number): string {
    const totalSeconds = Math.floor(milliseconds / 1000)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}:${seconds.toString().padStart(2, '0')}`
  }

  /**
   * 格式化文件大小
   */
  static formatFileSize(bytes: number): string {
    const units = ['B', 'KB', 'MB', 'GB']
    let size = bytes
    let unitIndex = 0

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024
      unitIndex++
    }

    return `${size.toFixed(1)} ${units[unitIndex]}`
  }
}
