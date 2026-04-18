/**
 * 播放质量监控服务（ArkTS 兼容版）
 * 监控播放过程中的各种质量指标，提供用户反馈
 */
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'PlaybackMonitor'
const DOMAIN = 0x0000

/**
 * 播放质量指标
 */
interface QualityMetrics {
  bufferUnderruns: number          // 缓冲区欠载次数
  networkErrors: number            // 网络错误次数
  syncErrors: number               // 同步错误次数
  totalPlayTime: number            // 总播放时长（毫秒）
  averageSyncDelay: number         // 平均同步延迟（毫秒）
  qualityScore: number             // 质量分数（0-100）
}

/**
 * 播放事件类型
 */
enum PlaybackEventType {
  BUFFER_UNDERRUN = 'buffer_underrun',
  NETWORK_ERROR = 'network_error',
  SYNC_ERROR = 'sync_error',
  PLAY_START = 'play_start',
  PLAY_STOP = 'play_stop',
  SYNC_SUCCESS = 'sync_success',
}

/**
 * 播放事件数据
 */
interface PlaybackEventData {
  delay?: number
}

/**
 * 播放事件
 */
class PlaybackEvent {
  type: PlaybackEventType
  timestamp: number
  data?: PlaybackEventData

  constructor(type: PlaybackEventType, timestamp: number, data?: PlaybackEventData) {
    this.type = type
    this.timestamp = timestamp
    this.data = data
  }
}

export class PlaybackMonitor {
  private events: PlaybackEvent[] = []
  private playStartTime: number = 0
  private totalPlayTime: number = 0
  private syncDelays: number[] = []

  // 质量阈值
  private readonly QUALITY_THRESHOLDS_GOOD = 80
  private readonly QUALITY_THRESHOLDS_FAIR = 60
  private readonly QUALITY_THRESHOLDS_POOR = 40

  /**
   * 开始播放监控
   */
  startMonitoring(): void {
    this.playStartTime = Date.now()
    this.recordEvent(PlaybackEventType.PLAY_START)
    hilog.info(DOMAIN, TAG, 'Playback monitoring started')
  }

  /**
   * 停止播放监控
   */
  stopMonitoring(): void {
    if (this.playStartTime > 0) {
      const sessionTime = Date.now() - this.playStartTime
      this.totalPlayTime += sessionTime
      this.playStartTime = 0
    }
    this.recordEvent(PlaybackEventType.PLAY_STOP)
    hilog.info(DOMAIN, TAG, 'Playback monitoring stopped')
  }

  /**
   * 记录缓冲区欠载事件
   */
  recordBufferUnderrun(): void {
    this.recordEvent(PlaybackEventType.BUFFER_UNDERRUN)
    hilog.warn(DOMAIN, TAG, 'Buffer underrun recorded')
  }

  /**
   * 记录网络错误事件
   */
  recordNetworkError(): void {
    this.recordEvent(PlaybackEventType.NETWORK_ERROR)
    hilog.error(DOMAIN, TAG, 'Network error recorded')
  }

  /**
   * 记录同步错误事件
   */
  recordSyncError(): void {
    this.recordEvent(PlaybackEventType.SYNC_ERROR)
    hilog.warn(DOMAIN, TAG, 'Sync error recorded')
  }

  /**
   * 记录同步成功事件
   * @param delay 同步延迟（毫秒）
   */
  recordSyncSuccess(delay: number): void {
    const data: PlaybackEventData = { delay: delay }
    this.recordEvent(PlaybackEventType.SYNC_SUCCESS, data)
    this.syncDelays.push(delay)
    hilog.debug(DOMAIN, TAG, `Sync success recorded: ${delay}ms`)
  }

  /**
   * 记录播放事件
   */
  private recordEvent(type: PlaybackEventType, data?: PlaybackEventData): void {
    const event = new PlaybackEvent(type, Date.now(), data)
    this.events.push(event)

    // 限制事件数量，避免内存溢出
    if (this.events.length > 1000) {
      this.events = this.events.slice(-500)  // 保留最近500个事件
    }
  }

  /**
   * 计算质量指标
   */
  getMetrics(): QualityMetrics {
    // 统计各类事件数量
    let bufferUnderruns = 0
    let networkErrors = 0
    let syncErrors = 0

    for (let i = 0; i < this.events.length; i++) {
      const event = this.events[i]
      if (event.type === PlaybackEventType.BUFFER_UNDERRUN) {
        bufferUnderruns++
      } else if (event.type === PlaybackEventType.NETWORK_ERROR) {
        networkErrors++
      } else if (event.type === PlaybackEventType.SYNC_ERROR) {
        syncErrors++
      }
    }

    // 计算平均同步延迟
    let averageSyncDelay = 0
    if (this.syncDelays.length > 0) {
      let sum = 0
      for (let i = 0; i < this.syncDelays.length; i++) {
        sum += this.syncDelays[i]
      }
      averageSyncDelay = sum / this.syncDelays.length
    }

    // 计算质量分数
    const qualityScore = this.calculateQualityScore(
      bufferUnderruns,
      networkErrors,
      syncErrors,
      this.totalPlayTime,
      averageSyncDelay
    )

    const metrics: QualityMetrics = {
      bufferUnderruns: bufferUnderruns,
      networkErrors: networkErrors,
      syncErrors: syncErrors,
      totalPlayTime: this.totalPlayTime,
      averageSyncDelay: averageSyncDelay,
      qualityScore: qualityScore
    }

    return metrics
  }

  /**
   * 计算质量分数（0-100）
   */
  private calculateQualityScore(
    bufferUnderruns: number,
    networkErrors: number,
    syncErrors: number,
    totalPlayTime: number,
    averageSyncDelay: number
  ): number {
    let score = 100

    // 缓冲区欠载扣分（每次扣5分）
    score -= bufferUnderruns * 5

    // 网络错误扣分（每次扣10分）
    score -= networkErrors * 10

    // 同步错误扣分（每次扣3分）
    score -= syncErrors * 3

    // 同步延迟扣分（超过100ms每100ms扣1分）
    if (averageSyncDelay > 100) {
      score -= Math.floor(averageSyncDelay / 100)
    }

    // 确保分数在0-100范围内
    return Math.max(0, Math.min(100, score))
  }

  /**
   * 获取质量评级
   */
  getQualityRating(): string {
    const metrics = this.getMetrics()
    const score = metrics.qualityScore

    if (score >= this.QUALITY_THRESHOLDS_GOOD) {
      return '良好'
    } else if (score >= this.QUALITY_THRESHOLDS_FAIR) {
      return '一般'
    } else if (score >= this.QUALITY_THRESHOLDS_POOR) {
      return '较差'
    } else {
      return '很差'
    }
  }

  /**
   * 获取质量建议
   */
  getQualityAdvice(): string[] {
    const metrics = this.getMetrics()
    const advice: string[] = []

    if (metrics.bufferUnderruns > 5) {
      advice.push('检测到频繁缓冲，建议检查网络连接或降低播放质量')
    }

    if (metrics.networkErrors > 3) {
      advice.push('网络不稳定，建议尝试重新连接或切换到更稳定的网络')
    }

    if (metrics.syncErrors > 10) {
      advice.push('进度同步出现问题，建议重启应用或检查设备连接')
    }

    if (metrics.averageSyncDelay > 500) {
      advice.push('同步延迟较高，可能影响播放体验，建议检查网络延迟')
    }

    if (advice.length === 0) {
      advice.push('播放质量良好，无需优化')
    }

    return advice
  }

  /**
   * 获取最近的事件
   */
  getRecentEvents(count: number): PlaybackEvent[] {
    return this.events.slice(-count)
  }

  /**
   * 清除所有监控数据
   */
  clear(): void {
    this.events = []
    this.playStartTime = 0
    this.totalPlayTime = 0
    this.syncDelays = []
    hilog.info(DOMAIN, TAG, 'Playback monitor cleared')
  }

  /**
   * 导出监控报告（用于调试）
   */
  exportReport(): string {
    const metrics = this.getMetrics()
    const rating = this.getQualityRating()
    const advice = this.getQualityAdvice()

    let report = '播放质量监控报告\n'
    report += '================\n'
    report += `质量分数: ${metrics.qualityScore}/100\n`
    report += `质量评级: ${rating}\n`
    report += `播放时长: ${Math.floor(metrics.totalPlayTime / 1000)}秒\n\n`

    report += '事件统计:\n'
    report += `- 缓冲区欠载: ${metrics.bufferUnderruns}次\n`
    report += `- 网络错误: ${metrics.networkErrors}次\n`
    report += `- 同步错误: ${metrics.syncErrors}次\n`
    report += `- 平均同步延迟: ${Math.floor(metrics.averageSyncDelay)}ms\n\n`

    report += '优化建议:\n'
    for (let i = 0; i < advice.length; i++) {
      report += `- ${advice[i]}\n`
    }

    return report.trim()
  }

  /**
   * 打印监控报告到日志
   */
  logReport(): void {
    const report = this.exportReport()
    hilog.info(DOMAIN, TAG, report)
  }
}

// 导出单例实例
export const playbackMonitor = new PlaybackMonitor()
