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
 *   └─ 委托给原始 DefaultAudioSink（本地 AudioTrack 播放）
 *
 * 音频格式从 configure() 调用中提取。
 */
class StreamingAudioSink(
    delegate: AudioSink,
    private val pcmRingBuffer: PcmRingBuffer,
    private val onFormatChanged: ((sampleRate: Int, channelCount: Int, encoding: Int) -> Unit)? = null
) : ForwardingAudioSink(delegate) {

    companion object {
        private const val TAG = "StreamingAudioSink"
    }

    private var lastSampleRate = 0
    private var lastChannelCount = 0
    private var lastEncoding = 0
    private var formatReported = false

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
        // 复制 PCM 数据到环形缓冲区
        if (buffer.hasRemaining()) {
            pcmRingBuffer.write(buffer.duplicate())

            // 延迟报告格式，确保在有实际数据时才通知
            if (!formatReported && lastSampleRate > 0) {
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
        super.flush()
    }

    /**
     * 重置时关闭环形缓冲区。
     */
    override fun reset() {
        pcmRingBuffer.reset()
        super.reset()
    }
}
