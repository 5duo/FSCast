package com.example.fscastremote.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.example.fscastremote.model.PcmAudioFormat

private const val TAG = "AudioTrackPlayer"

/**
 * PCM 音频播放器
 * 使用 AudioTrack 播放接收到的 PCM 数据
 */
class AudioTrackPlayer {
    private var audioTrack: AudioTrack? = null
    private var currentFormat: PcmAudioFormat? = null
    private var isPlaying = false

    /**
     * 配置音频格式
     */
    fun configure(format: PcmAudioFormat) {
        if (currentFormat == format && audioTrack != null) {
            return // 格式未变化，无需重建
        }

        release()

        val sampleRate = format.sampleRate
        val channelConfig = if (format.channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }

        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioEncoding) * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioEncoding)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        currentFormat = format
        Log.i(TAG, "AudioTrack configured: ${format.sampleRate}Hz, ${format.channels}ch, buffer=$bufferSize")
    }

    /**
     * 写入 PCM 数据
     */
    fun writePcmData(data: ByteArray) {
        val track = audioTrack
        if (track == null || !isPlaying) {
            return
        }

        var offset = 0
        while (offset < data.size) {
            val written = track.write(data, offset, data.size - offset)
            if (written <= 0) {
                Log.w(TAG, "AudioTrack write failed: $written")
                break
            }
            offset += written
        }
    }

    /**
     * 开始播放
     */
    fun play() {
        val track = audioTrack ?: run {
            Log.w(TAG, "Cannot play: AudioTrack not configured")
            return
        }

        if (track.state == AudioTrack.STATE_INITIALIZED) {
            track.play()
            isPlaying = true
            Log.i(TAG, "AudioTrack started")
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        audioTrack?.pause()
        isPlaying = false
        Log.i(TAG, "AudioTrack paused")
    }

    /**
     * 停止播放
     */
    fun stop() {
        audioTrack?.stop()
        isPlaying = false
        Log.i(TAG, "AudioTrack stopped")
    }

    /**
     * 清空缓冲区
     */
    fun flush() {
        audioTrack?.flush()
        Log.i(TAG, "AudioTrack flushed")
    }

    /**
     * 设置音量
     */
    fun setVolume(volume: Float) {
        audioTrack?.setVolume(volume)
    }

    /**
     * 释放资源
     */
    fun release() {
        audioTrack?.release()
        audioTrack = null
        currentFormat = null
        isPlaying = false
        Log.i(TAG, "AudioTrack released")
    }

    /**
     * 是否已配置
     */
    val isConfigured: Boolean
        get() = audioTrack != null

    /**
     * 是否正在播放
     */
    val getIsPlaying: Boolean
        get() = isPlaying
}
