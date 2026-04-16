package com.example.floatingscreencasting.audio

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * 包装 AudioSink，在 handleBuffer() 中拦截 PCM 数据并复制到 PcmRingBuffer。
 *
 * PCM 数据流向：
 * ExoPlayer → MediaCodecAudioRenderer → StreamingAudioSink.handleBuffer()
 *   ├─ 复制 PCM 到 PcmRingBuffer（网络传输用）
 *   ├─ 记录时间戳到时间戳队列（用于同步）
 *   └─ 委托给原始 DefaultAudioSink（本地 AudioTrack 播放）
 *
 * 音频格式从 configure() 调用中提取。
 */
class StreamingAudioSink(
    delegate: AudioSink,
    private val pcmRingBuffer: PcmRingBuffer,
    private val timestampQueue: TimestampQueue,
    private val onFormatChanged: ((sampleRate: Int, channelCount: Int, encoding: Int) -> Unit)? = null
) : ForwardingAudioSink(delegate) {

    companion object {
        private const val TAG = "StreamingAudioSink"
    }

    private var lastSampleRate = 0
    private var lastChannelCount = 0
    private var lastEncoding = 0
    private var formatReported = false
    private var formatReportedCount = 0  // 用于记录前几次的缓冲区信息
    private var handleBufferCount = 0  // handleBuffer 调用计数
    private var totalBytesCaptured = 0L  // 总捕获字节数
    private var lastHandleBufferTime = 0L  // 上次handleBuffer调用时间

    /**
     * 拦截音频配置，提取格式信息。
     */
    override fun configure(format: Format, isEncodingPassthrough: Int, outputEncoding: IntArray?) {
        // 提取音频格式
        val sampleRate = format.sampleRate
        val channelCount = format.channelCount
        val encoding = format.pcmEncoding.toInt()

        if (sampleRate != lastSampleRate || channelCount != lastChannelCount || encoding != lastEncoding) {
            Log.d(TAG, "音频格式变化: ${sampleRate}Hz, ${channelCount}ch, encoding=$encoding")
            lastSampleRate = sampleRate
            lastChannelCount = channelCount
            lastEncoding = encoding
            formatReported = false

            // 立即发送格式头（确保格式及时更新）
            Log.d(TAG, "立即发送格式头: ${sampleRate}Hz, ${channelCount}ch")
            onFormatChanged?.invoke(sampleRate, channelCount, encoding)
        }

        super.configure(format, isEncodingPassthrough, outputEncoding)
    }

    /**
     * 拦截 PCM 数据，复制到环形缓冲区。
     *
     * 注意：ByteBuffer 可能被 ExoPlayer 复用，必须在 handleBuffer 返回前复制数据。
     * duplicate() 创建共享底层数据的新 Buffer，position/limit 独立。
     */
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedSampleCount: Int): Boolean {
        handleBufferCount++

        // 计算handleBuffer调用间隔（用于诊断"快进"问题）
        val currentTime = System.currentTimeMillis()
        if (lastHandleBufferTime > 0 && handleBufferCount <= 30) {
            val interval = currentTime - lastHandleBufferTime
            // 完全基于实际字节数计算音频时长
            val bytesPerSample = 2  // 16-bit = 2字节
            val expectedInterval = 1000L * buffer.remaining() / (lastSampleRate * lastChannelCount * bytesPerSample)
            Log.d(TAG, "handleBuffer#$handleBufferCount: 实际间隔=${interval}ms, 期望间隔≈${expectedInterval}ms, 差异=${interval - expectedInterval}ms, 字节数=${buffer.remaining()}")
        }
        lastHandleBufferTime = currentTime

        // 复制 PCM 数据到环形缓冲区
        if (buffer.hasRemaining()) {
            val remaining = buffer.remaining()
            val position = buffer.position()

            // 记录缓冲区信息（扩大范围用于调试）
            if (formatReportedCount < 100) {
                Log.d(TAG, "handleBuffer#${handleBufferCount}: 字节数=$remaining, position=$position, limit=${buffer.limit()}, 容量=${buffer.capacity()}, 直接缓冲=${buffer.isDirect}, 字节序=${buffer.order()}")
                Log.d(TAG, "handleBuffer#${handleBufferCount}: 音频格式 - ${lastSampleRate}Hz, ${lastChannelCount}ch, encoding=$lastEncoding, encodedSampleCount=$encodedSampleCount")
                formatReportedCount++
            }

            // 完全忽略encodedSampleCount，因为日志显示它不可靠
            // 基于16位PCM计算实际的音频帧数和采样数
            val bytesPerFrame = lastChannelCount * 2  // 立体声16位 = 4字节/帧
            val actualFrameCount = remaining / bytesPerFrame
            val actualSampleCount = remaining / 2  // 16位采样点数

            if (handleBufferCount <= 100) {
                // 记录实际数据量，忽略encodedSampleCount
                val audioDurationMs = 1000.0 * actualFrameCount / lastSampleRate
                Log.d(TAG, "handleBuffer#${handleBufferCount}: 实际数据 - 字节数=$remaining, 音频帧数=$actualFrameCount, 采样点数=$actualSampleCount, 音频时长≈${audioDurationMs}ms")
                Log.d(TAG, "handleBuffer#${handleBufferCount}: encodedSampleCount=$encodedSampleCount (已忽略，基于实际字节数处理)")
            }
            // 关键修复：使用duplicate()创建独立的ByteBuffer视图
            // 避免在读取期间影响原始buffer的position
            val duplicate = buffer.duplicate()
            val tempArray = ByteArray(remaining)
            duplicate.get(tempArray)  // 从duplicate读取，不影响原buffer

            // 验证：确保原buffer的position没有被改变
            if (handleBufferCount <= 5) {
                val positionAfter = buffer.position()
                if (positionAfter != position) {
                    Log.e(TAG, "handleBuffer#$handleBufferCount: 警告 - buffer.position被改变！原始=$position, 现在=$positionAfter")
                } else {
                    Log.d(TAG, "handleBuffer#$handleBufferCount: 验证通过 - buffer.position保持不变=$position")
                }
            }

            // 验证数据完整性（扩大范围用于调试）
            if (handleBufferCount <= 100) {
                // 检查前100字节是否全为0
                var hasNonZero = false
                var zeroCount = 0
                for (i in 0 until minOf(100, remaining)) {
                    if (tempArray[i].toInt() != 0) {
                        hasNonZero = true
                    } else {
                        zeroCount++
                    }
                }
                Log.d(TAG, "handleBuffer#${handleBufferCount}: 前100字节分析 - 有数据=$hasNonZero, 零字节数=$zeroCount")

                // 分析16位PCM采样值（LITTLE_ENDIAN）验证数据正确性
                if (hasNonZero && remaining >= 8) {
                    val samplesToAnalyze = minOf(20, remaining / 2)  // 分析前20个采样点
                    val sampleValues = mutableListOf<Int>()
                    var hasSignal = false

                    for (i in 0 until samplesToAnalyze) {
                        val index = i * 2
                        // LITTLE_ENDIAN: 低字节在前
                        val low = tempArray[index].toInt() and 0xFF
                        val high = tempArray[index + 1].toInt() and 0xFF
                        val sample = (high shl 8) or low
                        // 转换为有符号16位整数
                        val signedSample = if (sample > 32767) sample - 65536 else sample
                        sampleValues.add(signedSample)
                        if (signedSample != 0) hasSignal = true
                    }

                    Log.d(TAG, "handleBuffer#${handleBufferCount}: 前${samplesToAnalyze}个采样值(LITTLE_ENDIAN): ${sampleValues.joinToString(", ")}")
                    Log.d(TAG, "handleBuffer#${handleBufferCount}: 采样率=${lastSampleRate}Hz, 声道数=${lastChannelCount}, 总字节数=$remaining, 总采样点数=${remaining / 2}, 音频帧数=$actualFrameCount, 时长≈${1000.0 * actualFrameCount / lastSampleRate}ms")

                    // 计算音频信号统计
                    var sum = 0L
                    var maxAbs = 0
                    var nonZeroCount = 0
                    val totalSamples = remaining / 2
                    for (i in 0 until totalSamples) {
                        val index = i * 2
                        val low = tempArray[index].toInt() and 0xFF
                        val high = tempArray[index + 1].toInt() and 0xFF
                        val sample = (high shl 8) or low
                        val signedSample = if (sample > 32767) sample - 65536 else sample
                        sum += signedSample
                        val abs = kotlin.math.abs(signedSample)
                        if (abs > maxAbs) maxAbs = abs
                        if (signedSample != 0) nonZeroCount++
                    }
                    val avg = sum / totalSamples
                    val nonZeroRatio = if (totalSamples > 0) nonZeroCount * 100.0 / totalSamples else 0.0
                    Log.d(TAG, "handleBuffer#${handleBufferCount}: 信号统计 - 采样点数=$totalSamples, 平均值=$avg, 最大绝对值=$maxAbs, 非零比例=$nonZeroRatio%, 有信号=$hasSignal")

                    // 额外检查：验证字节序是否正确
                    // 如果字节序错了，数据会看起来像随机噪声
                    Log.d(TAG, "handleBuffer#${handleBufferCount}: 字节序验证 - ByteBuffer.order()=${buffer.order()}")
                }
            }

            // 将独立数组写入环形缓冲区
            val bytesWritten = pcmRingBuffer.write(tempArray)
            totalBytesCaptured += bytesWritten

            // 每1MB记录一次统计
            if (totalBytesCaptured > formatReportedCount * 1024 * 1024) {
                val mb = totalBytesCaptured / (1024 * 1024)
                Log.d(TAG, "已捕获 ${mb}MB PCM 数据，调用次数: $handleBufferCount, 格式: ${lastSampleRate}Hz, ${lastChannelCount}ch")
                formatReportedCount++
            }

            // 记录时间戳（与写入的字节数对应）
            timestampQueue.write(presentationTimeUs, bytesWritten)

            // 延迟报告格式，确保在有实际数据时才通知
            if (!formatReported && lastSampleRate > 0) {
                Log.d(TAG, "首次报告格式: ${lastSampleRate}Hz, ${lastChannelCount}ch, encoding=$lastEncoding")
                formatReported = true
                onFormatChanged?.invoke(lastSampleRate, lastChannelCount, lastEncoding)
            }
        }

        return super.handleBuffer(buffer, presentationTimeUs, encodedSampleCount)
    }

    /**
     * flush 时同时清空环形缓冲区（seek 场景）。
     */
    override fun flush() {
        pcmRingBuffer.flush()
        timestampQueue.clear()
        super.flush()
    }

    /**
     * 重置时关闭环形缓冲区。
     */
    override fun reset() {
        pcmRingBuffer.reset()
        timestampQueue.clear()
        super.reset()
    }
}
