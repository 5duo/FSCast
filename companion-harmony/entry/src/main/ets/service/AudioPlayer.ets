/**
 * 音频播放器
 * 使用 AudioRenderer 播放实时 PCM 流
 */
import { audio } from '@kit.AudioKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { AudioFormat } from '../model/Models'
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'AudioPlayer'
const DOMAIN = 0x0000

export class AudioPlayer {
  private audioRenderer: audio.AudioRenderer | null = null
  private currentFormat: AudioFormat | null = null
  private isPlaying: boolean = false

  // 使用队列缓冲 PCM 数据
  private pcmQueue: Uint8Array[] = []
  private totalQueueSize: number = 0
  private currentChunk: Uint8Array | null = null
  private currentReadOffset: number = 0

  // 计算当前音频格式的每秒字节数
  private get bytesPerSecond(): number {
    if (!this.currentFormat) return 176400  // 默认 44.1kHz
    return this.currentFormat.sampleRate * this.currentFormat.channels * 2  // 16-bit = 2 bytes
  }

  // 缓冲区配置（基于实际采样率）
  // 实时流：需要足够缓冲防止underrun，但不能太大导致延迟
  private get MAX_QUEUE_SIZE(): number { return this.bytesPerSecond * 10 }  // 最大 10 秒（防止网络抖动）
  private get MIN_START_BUFFER(): number { return this.bytesPerSecond * 1.5 }  // 启动前缓冲 1.5 秒（增加缓冲减少underrun）

  // writeData 回调注册状态
  private writeDataRegistered: boolean = false

  // 预缓冲状态
  private needPrebuffer: boolean = true

  // 统计
  private underrunCount: number = 0
  private totalWritten: number = 0
  private lastLogTime: number = 0
  private consecutiveUnderruns: number = 0
  private lastUnderrunPosition: number = 0  // 记录上次 underrun 位置
  private writeCount: number = 0  // 记录 writeData 调用次数
  private lastRequestedBufferSize: number = 0  // 记录上次请求的缓冲区大小
  private lastBufferSizeReportTime: number = 0  // 记录上次缓冲区报告时间
  private callbackTimestamps: number[] = []  // 记录回调时间戳，用于计算频率

  // 时间戳同步
  private baseTimestamp: number = 0  // 第一个有效时间戳
  private baseTimeMs: number = 0     // 第一个时间戳对应的系统时间
  private hasValidTimestamp: boolean = false  // 是否有有效时间戳

  // 缓冲区大小变化回调（用于流量控制）
  onBufferSizeChanged: ((currentSize: number, maxSize: number) => void) | null = null

  /**
   * 配置音频格式并创建播放器
   */
  async configure(format: AudioFormat): Promise<void> {
    if (this.currentFormat && this.currentFormat.equals(format) && this.audioRenderer) {
      hilog.info(DOMAIN, TAG, 'Same format, skip reconfigure')
      return
    }

    hilog.info(DOMAIN, TAG, `Configure: ${format.sampleRate}Hz, ${format.channels}ch`)

    await this.stop()
    await this.release()

    this.currentFormat = format

    // 创建 AudioRenderer
    let audioStreamInfo: audio.AudioStreamInfo = {
      samplingRate: format.sampleRate as audio.AudioSamplingRate,
      channels: format.channels as audio.AudioChannel,
      sampleFormat: audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,
      encodingType: audio.AudioEncodingType.ENCODING_TYPE_RAW
    }

    let audioRendererInfo: audio.AudioRendererInfo = {
      usage: audio.StreamUsage.STREAM_USAGE_MUSIC,
      rendererFlags: 0
    }

    let audioRendererOptions: audio.AudioRendererOptions = {
      streamInfo: audioStreamInfo,
      rendererInfo: audioRendererInfo
    }

    try {
      this.audioRenderer = await audio.createAudioRenderer(audioRendererOptions)
      hilog.info(DOMAIN, TAG, 'AudioRenderer created')
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'Create AudioRenderer failed: ' + (e as BusinessError).message)
    }
  }

  /**
   * 开始播放
   */
  async start(): Promise<void> {
    if (!this.audioRenderer) {
      hilog.warn(DOMAIN, TAG, 'Not configured')
      return
    }

    const state = this.audioRenderer.state
    hilog.info(DOMAIN, TAG, `Start: state=${state}, queue=${this.totalQueueSize}`)

    if (state === audio.AudioState.STATE_PREPARED ||
        state === audio.AudioState.STATE_PAUSED ||
        state === audio.AudioState.STATE_STOPPED) {
      try {
        if (!this.writeDataRegistered) {
          this.audioRenderer.on('writeData', (buffer: ArrayBuffer): audio.AudioDataCallbackResult => {
            return this.onWriteData(buffer)
          })
          this.writeDataRegistered = true
        }

        // 检查是否有足够缓冲再启动（避免立即underrun）
        const bufferSec = this.totalQueueSize / this.bytesPerSecond
        if (bufferSec < 0.5 && this.needPrebuffer) {
          hilog.warn(DOMAIN, TAG, `Buffer too low: ${bufferSec.toFixed(2)}s, waiting for data...`)
          // 不启动，等待更多数据
          return
        }

        await this.audioRenderer.start()
        this.isPlaying = true
        this.underrunCount = 0
        this.totalWritten = 0
        this.needPrebuffer = false  // 已启动，不再需要预缓冲
        hilog.info(DOMAIN, TAG, `AudioRenderer started with ${bufferSec.toFixed(2)}s buffer`)
      } catch (e) {
        hilog.error(DOMAIN, TAG, 'Start failed: ' + (e as BusinessError).message)
      }
    }
  }

  /**
   * writeData 回调
   */
  private onWriteData(buffer: ArrayBuffer): audio.AudioDataCallbackResult {
    const now = Date.now()
    const dstView = new Uint8Array(buffer)
    const requestedSize = dstView.length  // 记录系统请求的缓冲区大小
    let written = 0
    let hasUnderrun = false

    // 记录回调时间戳（用于计算频率）
    this.callbackTimestamps.push(now)
    if (this.callbackTimestamps.length > 20) {
      this.callbackTimestamps.shift()  // 只保留最近20次
    }

    // 日志：记录请求大小（前几次）
    if (this.writeCount < 20) {
      hilog.info(DOMAIN, TAG, `writeData#${this.writeCount}: requested=${requestedSize}, queue=${this.totalQueueSize} bytes`)
      this.writeCount++
      this.lastRequestedBufferSize = requestedSize
    }

    // 从当前块读取
    while (written < dstView.length) {
      // 获取当前块
      if (!this.currentChunk) {
        if (this.pcmQueue.length === 0) {
          // 无数据，填充静音（PCM 16-bit 静音 = 0）
          // 使用 memset 更高效
          for (let i = written; i < dstView.length; i++) {
            dstView[i] = 0
          }
          this.underrunCount++
          this.consecutiveUnderruns++
          hasUnderrun = true
          break
        }
        this.currentChunk = this.pcmQueue[0]
        this.currentReadOffset = 0
      }

      // 计算可读取的字节数
      const availableInChunk = this.currentChunk.length - this.currentReadOffset
      const toCopy = Math.min(availableInChunk, dstView.length - written)

      // 复制数据（使用 set 更高效）
      dstView.set(this.currentChunk.subarray(this.currentReadOffset, this.currentReadOffset + toCopy), written)

      written += toCopy
      this.currentReadOffset += toCopy
      this.totalQueueSize -= toCopy
      this.totalWritten += toCopy

      // 当前块用完
      if (this.currentReadOffset >= this.currentChunk.length) {
        this.pcmQueue.shift()
        this.currentChunk = null
        this.currentReadOffset = 0
      }

      // 有数据时重置连续 underrun 计数
      if (!hasUnderrun) {
        this.consecutiveUnderruns = 0
      }
    }

    // 统计日志（减少频率）
    if (now - this.lastLogTime > 10000) {  // 改为每 10 秒
      const avgRate = Math.floor(this.totalWritten / 10)
      const bufferSec = (this.totalQueueSize / this.bytesPerSecond).toFixed(2)
      // 计算实际写入率（考虑写入的字节数和请求的字节数）
      const fillRate = written > 0 ? Math.floor((written / requestedSize) * 100) : 0

      // 计算实际回调频率和消耗速率
      let callbackFreq = 0
      let consumptionRate = 0
      if (this.callbackTimestamps.length >= 2) {
        const timeSpan = this.callbackTimestamps[this.callbackTimestamps.length - 1] - this.callbackTimestamps[0]
        const callbackCount = this.callbackTimestamps.length - 1
        callbackFreq = Math.floor(callbackCount * 1000 / timeSpan)  // Hz
        consumptionRate = callbackFreq * this.lastRequestedBufferSize  // B/s
      }

      hilog.info(DOMAIN, TAG, `Stats: written=${this.totalWritten}, rate=${avgRate} B/s, queue=${bufferSec}s, underruns=${this.underrunCount}, fill=${fillRate}%, callback_freq=${callbackFreq}Hz, consumption=${consumptionRate} B/s`)
      this.totalWritten = 0
      this.lastLogTime = now
    }

    // 每秒通知缓冲区状态（用于流量控制）
    if (now - this.lastBufferSizeReportTime > 1000) {
      this.onBufferSizeChanged?.(this.totalQueueSize, this.MAX_QUEUE_SIZE)
      this.lastBufferSizeReportTime = now
    }

    // 检测连续 underrun（可能需要调整缓冲区）
    if (this.consecutiveUnderruns > 20) {  // 增加阈值到 20
      const bufferSec = (this.totalQueueSize / this.bytesPerSecond).toFixed(2)
      hilog.warn(DOMAIN, TAG, `Consecutive underruns: ${this.consecutiveUnderruns}, buffer=${bufferSec}s, requested=${requestedSize}`)
      this.consecutiveUnderruns = 0
    }

    // 检测队列健康状态
    if (this.writeCount === 20) {
      const bufferSec = (this.totalQueueSize / this.bytesPerSecond).toFixed(2)
      const avgWritten = Math.floor(this.totalWritten / 20)
      hilog.info(DOMAIN, TAG, `Initial 20 callbacks: avg_written=${avgWritten}, buffer=${bufferSec}s, requested=${requestedSize}`)
    }

    return audio.AudioDataCallbackResult.VALID
  }

  /**
   * 写入 PCM 数据（带时间戳）
   */
  writePcmData(data: ArrayBuffer, timestamp: number): void {
    // 初始化时间戳基准（只记录，不调整）
    if (!this.hasValidTimestamp && timestamp > 0) {
      this.baseTimestamp = timestamp
      this.baseTimeMs = Date.now()
      this.hasValidTimestamp = true
      hilog.info(DOMAIN, TAG, `First PCM: ${data.byteLength} bytes, ts=${timestamp}`)
    }

    const dataView = new Uint8Array(data)

    // 直接添加到队列，不做任何调整
    this.pcmQueue.push(dataView)
    this.totalQueueSize += dataView.byteLength

    // 硬限制：缓冲区超过最大值时丢弃最旧的数据
    while (this.totalQueueSize > this.MAX_QUEUE_SIZE && this.pcmQueue.length > 1) {
      const removed = this.pcmQueue.shift()
      this.totalQueueSize -= removed!.byteLength

      if (this.currentChunk === removed) {
        this.currentChunk = null
        this.currentReadOffset = 0
      }

      hilog.warn(DOMAIN, TAG, `Buffer overflow: dropped ${removed!.byteLength} bytes`)
    }

    // 检查是否满足启动条件（自动启动播放器）
    if (this.needPrebuffer && this.audioRenderer) {
      const bufferSec = this.totalQueueSize / this.bytesPerSecond
      // 当缓冲达到0.5秒时自动启动
      if (bufferSec >= 0.5) {
        this.needPrebuffer = false
        hilog.info(DOMAIN, TAG, `Prebuffer complete: ${bufferSec.toFixed(2)}s, auto-starting...`)
        this.start()  // 自动启动
      }
    }
  }

  /**
   * 暂停播放
   */
  async pause(): Promise<void> {
    if (!this.audioRenderer) return

    if (this.audioRenderer.state === audio.AudioState.STATE_RUNNING) {
      try {
        await this.audioRenderer.pause()
        this.isPlaying = false
        hilog.info(DOMAIN, TAG, 'AudioRenderer paused')
      } catch (e) {
        hilog.error(DOMAIN, TAG, 'Pause failed: ' + (e as BusinessError).message)
      }
    }

    // 暂停后不清空队列，保持缓冲数据，下次启动时可以快速恢复
  }

  /**
   * 停止播放
   */
  async stop(): Promise<void> {
    if (!this.audioRenderer) return

    const state = this.audioRenderer.state
    if (state === audio.AudioState.STATE_RUNNING || state === audio.AudioState.STATE_PAUSED) {
      try {
        await this.audioRenderer.stop()
        this.isPlaying = false
        this.clearQueue()
        hilog.info(DOMAIN, TAG, 'AudioRenderer stopped')
      } catch (e) {
        hilog.error(DOMAIN, TAG, 'Stop failed: ' + (e as BusinessError).message)
      }
    }
  }

  /**
   * Flush
   */
  async flush(): Promise<void> {
    this.clearQueue()
    if (!this.audioRenderer) return
    const state = this.audioRenderer.state
    if (state === audio.AudioState.STATE_RUNNING || state === audio.AudioState.STATE_PAUSED) {
      try {
        await this.audioRenderer.flush()
      } catch (e) {
        hilog.error(DOMAIN, TAG, 'Flush failed: ' + (e as BusinessError).message)
      }
    }
    // 时间戳已在 clearQueue 中重置
  }

  /**
   * 清空队列
   */
  private clearQueue(): void {
    this.pcmQueue = []
    this.currentChunk = null
    this.currentReadOffset = 0
    this.totalQueueSize = 0
    this.underrunCount = 0
    this.totalWritten = 0
    this.consecutiveUnderruns = 0
    this.needPrebuffer = true  // 重置预缓冲状态
    this.writeCount = 0  // 重置 writeData 计数
    // 清空队列时重置时间戳，让同步重新开始
    this.hasValidTimestamp = false
    this.baseTimestamp = 0
    this.baseTimeMs = 0
  }

  /**
   * 释放资源
   */
  async release(): Promise<void> {
    if (this.audioRenderer) {
      try {
        if (this.writeDataRegistered) {
          this.audioRenderer.off('writeData')
          this.writeDataRegistered = false
        }
        if (this.audioRenderer.state !== audio.AudioState.STATE_RELEASED) {
          await this.audioRenderer.release()
        }
      } catch (e) {
        hilog.error(DOMAIN, TAG, 'AudioRenderer release failed: ' + (e as BusinessError).message)
      }
      this.audioRenderer = null
    }

    this.isPlaying = false
    this.clearQueue()
    this.currentFormat = null
    hilog.info(DOMAIN, TAG, 'Released')
  }

  getIsPlaying(): boolean {
    return this.isPlaying
  }

  getBufferSize(): number {
    return this.totalQueueSize
  }
}
