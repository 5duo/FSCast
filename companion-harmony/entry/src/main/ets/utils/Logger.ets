/**
 * 统一日志工具
 * 提供一致的日志格式和日志级别控制
 */
import { hilog } from '@kit.PerformanceAnalysisKit'

export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3
}

export class Logger {
  private tag: string
  private domain: number
  private minLevel: LogLevel

  constructor(tag: string, domain: number = 0x0000, minLevel: LogLevel = LogLevel.DEBUG) {
    this.tag = tag
    this.domain = domain
    this.minLevel = minLevel
  }

  /**
   * 设置最低日志级别
   */
  setMinLevel(level: LogLevel): void {
    this.minLevel = level
  }

  /**
   * 调试日志
   */
  debug(message: string, ...params: Object[]): void {
    if (this.minLevel <= LogLevel.DEBUG) {
      hilog.debug(this.domain, this.tag, message, ...params)
    }
  }

  /**
   * 普通日志
   */
  info(message: string, ...params: Object[]): void {
    if (this.minLevel <= LogLevel.INFO) {
      hilog.info(this.domain, this.tag, message, ...params)
    }
  }

  /**
   * 警告日志
   */
  warn(message: string, ...params: Object[]): void {
    if (this.minLevel <= LogLevel.WARN) {
      hilog.warn(this.domain, this.tag, message, ...params)
    }
  }

  /**
   * 错误日志
   */
  error(message: string, ...params: Object[]): void {
    if (this.minLevel <= LogLevel.ERROR) {
      hilog.error(this.domain, this.tag, message, ...params)
    }
  }

  /**
   * 带分隔线的日志（用于重要事件）
   */
  infoWithDivider(message: string, char: string = '=', length: number = 40): void {
    const divider = char.repeat(length)
    this.info(divider)
    this.info(message)
    this.info(divider)
  }

  /**
   * 带分隔线的错误日志
   */
  errorWithDivider(message: string, char: string = '=', length: number = 40): void {
    const divider = char.repeat(length)
    this.error(divider)
    this.error(message)
    this.error(divider)
  }

  /**
   * 打印对象（格式化JSON）
   */
  logObject(label: string, obj: Object): void {
    this.info(`${label}: ${JSON.stringify(obj)}`)
  }

  /**
   * 打印函数执行时间
   */
  async time<T>(label: string, fn: () => Promise<T>): Promise<T> {
    const start = Date.now()
    this.debug(`[Timer] ${label} started`)
    try {
      const result = await fn()
      const elapsed = Date.now() - start
      this.info(`[Timer] ${label} completed in ${elapsed}ms`)
      return result
    } catch (error) {
      const elapsed = Date.now() - start
      this.error(`[Timer] ${label} failed after ${elapsed}ms: ${JSON.stringify(error)}`)
      throw error
    }
  }

  /**
   * 打印函数执行时间（同步版本）
   */
  timeSync<T>(label: string, fn: () => T): T {
    const start = Date.now()
    this.debug(`[Timer] ${label} started`)
    try {
      const result = fn()
      const elapsed = Date.now() - start
      this.info(`[Timer] ${label} completed in ${elapsed}ms`)
      return result
    } catch (error) {
      const elapsed = Date.now() - start
      this.error(`[Timer] ${label} failed after ${elapsed}ms: ${JSON.stringify(error)}`)
      throw error
    }
  }
}

/**
 * 快速创建Logger实例
 */
export function createLogger(tag: string, domain: number = 0x0000): Logger {
  return new Logger(tag, domain)
}

// 默认日志级别（可根据构建配置调整）
Logger.globalMinLevel = LogLevel.DEBUG
