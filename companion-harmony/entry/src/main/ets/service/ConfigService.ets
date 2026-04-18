/**
 * 配置服务（ArkTS 兼容版）
 * 使用 Preferences API 持久化应用配置
 */
import { preferences } from '@kit.ArkData'
import { Context } from '@kit.AbilityKit'
import { AppConfig } from '../constants/Config'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { BusinessError } from '@kit.BasicServicesKit'

const TAG = 'ConfigService'
const DOMAIN = 0x0000
const PREF_NAME = 'fscast_config'

/**
 * 配置项接口
 */
interface ConfigOptions {
  carIp?: string
  volume?: number
  playbackSpeed?: number
}

export class ConfigService {
  private static instance: ConfigService
  private preferences: preferences.Preferences | null = null
  private context: Context | null = null

  private constructor() {}

  /**
   * 获取单例实例
   */
  static getInstance(): ConfigService {
    if (!ConfigService.instance) {
      ConfigService.instance = new ConfigService()
    }
    return ConfigService.instance
  }

  /**
   * 初始化配置服务
   */
  async initialize(context: Context): Promise<void> {
    try {
      this.context = context
      this.preferences = await preferences.getPreferences(context, PREF_NAME)
      hilog.info(DOMAIN, TAG, 'ConfigService initialized')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to initialize ConfigService: ${JSON.stringify(error)}`)
      const err = error as BusinessError
      throw new Error(`ConfigService init failed: ${err.message}`)
    }
  }

  /**
   * 确保已初始化
   */
  private ensureInitialized(): void {
    if (!this.preferences) {
      throw new Error('ConfigService not initialized. Call initialize() first.')
    }
  }

  // ==================== 车机 IP 配置 ====================

  /**
   * 获取车机 IP 地址
   */
  async getCarIp(): Promise<string> {
    this.ensureInitialized()
    try {
      const ip = await this.preferences!.get('car_ip', AppConfig.DEFAULTS.CAR_IP) as string
      hilog.debug(DOMAIN, TAG, `Get car IP: ${ip}`)
      return ip
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to get car IP: ${JSON.stringify(error)}`)
      return AppConfig.DEFAULTS.CAR_IP
    }
  }

  /**
   * 设置车机 IP 地址
   */
  async setCarIp(ip: string): Promise<void> {
    this.ensureInitialized()
    try {
      // 验证 IP 格式
      if (!AppConfig.isValidIp(ip)) {
        throw new Error(`Invalid IP address format: ${ip}`)
      }

      await this.preferences!.put('car_ip', ip)
      await this.preferences!.flush()
      hilog.info(DOMAIN, TAG, `Car IP saved: ${ip}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to save car IP: ${JSON.stringify(error)}`)
      throw new Error(`Failed to save car IP: ${ip}`)
    }
  }

  // ==================== 音量配置 ====================

  /**
   * 获取音量设置
   */
  async getVolume(): Promise<number> {
    this.ensureInitialized()
    try {
      const volume = await this.preferences!.get('volume', AppConfig.DEFAULTS.VOLUME) as number
      // 确保音量在有效范围内
      return Math.max(0.0, Math.min(1.0, volume))
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to get volume: ${JSON.stringify(error)}`)
      return AppConfig.DEFAULTS.VOLUME
    }
  }

  /**
   * 设置音量
   */
  async setVolume(volume: number): Promise<void> {
    this.ensureInitialized()
    try {
      // 限制音量范围
      const clampedVolume = Math.max(0.0, Math.min(1.0, volume))
      await this.preferences!.put('volume', clampedVolume)
      await this.preferences!.flush()
      hilog.info(DOMAIN, TAG, `Volume saved: ${clampedVolume}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to save volume: ${JSON.stringify(error)}`)
      throw new Error('Failed to save volume')
    }
  }

  // ==================== 播放速度配置 ====================

  /**
   * 获取播放速度
   */
  async getPlaybackSpeed(): Promise<number> {
    this.ensureInitialized()
    try {
      const speed = await this.preferences!.get('playback_speed', AppConfig.DEFAULTS.PLAYBACK_SPEED) as number
      // 确保速度在合理范围内
      return Math.max(0.5, Math.min(2.0, speed))
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to get playback speed: ${JSON.stringify(error)}`)
      return AppConfig.DEFAULTS.PLAYBACK_SPEED
    }
  }

  /**
   * 设置播放速度
   */
  async setPlaybackSpeed(speed: number): Promise<void> {
    this.ensureInitialized()
    try {
      // 限制速度范围
      const clampedSpeed = Math.max(0.5, Math.min(2.0, speed))
      await this.preferences!.put('playback_speed', clampedSpeed)
      await this.preferences!.flush()
      hilog.info(DOMAIN, TAG, `Playback speed saved: ${clampedSpeed}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to save playback speed: ${JSON.stringify(error)}`)
      throw new Error('Failed to save playback speed')
    }
  }

  // ==================== 通用配置方法 ====================

  /**
   * 获取配置项
   */
  async get(key: string, defaultValue: ESObject): Promise<ESObject> {
    this.ensureInitialized()
    try {
      return await this.preferences!.get(key, defaultValue)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to get config '${key}': ${JSON.stringify(error)}`)
      return defaultValue
    }
  }

  /**
   * 设置配置项
   */
  async set(key: string, value: ESObject): Promise<void> {
    this.ensureInitialized()
    try {
      await this.preferences!.put(key, value)
      await this.preferences!.flush()
      hilog.debug(DOMAIN, TAG, `Config saved: ${key}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to save config '${key}': ${JSON.stringify(error)}`)
      throw new Error(`Failed to save config: ${key}`)
    }
  }

  /**
   * 批量设置配置
   */
  async setMultiple(options: ConfigOptions): Promise<void> {
    this.ensureInitialized()
    try {
      if (options.carIp !== undefined) {
        await this.preferences!.put('car_ip', options.carIp)
      }
      if (options.volume !== undefined) {
        await this.preferences!.put('volume', options.volume)
      }
      if (options.playbackSpeed !== undefined) {
        await this.preferences!.put('playback_speed', options.playbackSpeed)
      }
      await this.preferences!.flush()

      const keys: string[] = []
      if (options.carIp !== undefined) keys.push('car_ip')
      if (options.volume !== undefined) keys.push('volume')
      if (options.playbackSpeed !== undefined) keys.push('playback_speed')

      hilog.info(DOMAIN, TAG, `Multiple configs saved: ${keys.join(', ')}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to save multiple configs: ${JSON.stringify(error)}`)
      throw new Error('Failed to save multiple configs')
    }
  }

  /**
   * 删除配置项
   */
  async remove(key: string): Promise<void> {
    this.ensureInitialized()
    try {
      await this.preferences!.delete(key)
      await this.preferences!.flush()
      hilog.info(DOMAIN, TAG, `Config removed: ${key}`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to remove config '${key}': ${JSON.stringify(error)}`)
      throw new Error(`Failed to remove config: ${key}`)
    }
  }

  /**
   * 清空所有配置
   */
  async clear(): Promise<void> {
    this.ensureInitialized()
    try {
      await this.preferences!.clear()
      await this.preferences!.flush()
      hilog.info(DOMAIN, TAG, 'All configs cleared')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to clear configs: ${JSON.stringify(error)}`)
      throw new Error('Failed to clear configs')
    }
  }

  /**
   * 检查配置项是否存在
   */
  async has(key: string): Promise<boolean> {
    this.ensureInitialized()
    try {
      return await this.preferences!.has(key)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to check config '${key}': ${JSON.stringify(error)}`)
      return false
    }
  }

  /**
   * 获取所有配置项的键
   */
  async getAllKeys(): Promise<string[]> {
    this.ensureInitialized()
    try {
      const all = await this.preferences!.getAll()
      return Object.keys(all) as string[]
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to get all keys: ${JSON.stringify(error)}`)
      return []
    }
  }

  /**
   * 导出所有配置（用于调试）
   */
  async exportConfig(): Promise<Record<string, Object | string | number | boolean>> {
    this.ensureInitialized()
    try {
      const keys = await this.getAllKeys()
      const config: Record<string, Object | string | number | boolean> = {}

      for (let i = 0; i < keys.length; i++) {
        const key = keys[i]
        config[key] = await this.get(key, '')
      }

      return config
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to export config: ${JSON.stringify(error)}`)
      return {}
    }
  }

  /**
   * 重置为默认配置
   */
  async resetToDefaults(): Promise<void> {
    try {
      await this.clear()
      await this.setCarIp(AppConfig.DEFAULTS.CAR_IP)
      await this.setVolume(AppConfig.DEFAULTS.VOLUME)
      await this.setPlaybackSpeed(AppConfig.DEFAULTS.PLAYBACK_SPEED)
      hilog.info(DOMAIN, TAG, 'Config reset to defaults')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to reset config: ${JSON.stringify(error)}`)
      throw new Error('Failed to reset config')
    }
  }

  /**
   * 释放资源
   */
  async release(): Promise<void> {
    try {
      if (this.preferences) {
        // Preferences 不需要显式释放
        this.preferences = null
      }
      hilog.info(DOMAIN, TAG, 'ConfigService released')
    } catch (error) {
      hilog.error(DOMAIN, TAG, `Failed to release ConfigService: ${JSON.stringify(error)}`)
    }
  }
}

// 导出单例实例
export const configService = ConfigService.getInstance()
