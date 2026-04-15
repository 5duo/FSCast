package com.example.floatingscreencasting.audio

import android.util.Log
import java.nio.ByteBuffer

/**
 * 线程安全的环形缓冲区，用于 PCM 音频数据传输。
 *
 * 写入端：ExoPlayer 音频渲染线程（高频写入）
 * 读取端：TCP 发送协程（网络发送）
 *
 * 缓冲区大小默认约 500ms 的 PCM 数据（44.1kHz 立体声 16bit = ~176KB）
 */
class PcmRingBuffer(
    capacityBytes: Int = DEFAULT_CAPACITY
) {
    companion object {
        private const val TAG = "PcmRingBuffer"
        // 44100 * 2(声道) * 2(16bit) * 0.5(秒) ≈ 88200，取 176400 约 1 秒缓冲
        private const val DEFAULT_CAPACITY = 176_400
    }

    private val buffer = ByteArray(capacityBytes)
    private val lock = Object()

    // 写入位置（下一个写入的字节索引）
    private var writePos = 0
    // 读取位置（下一个读取的字节索引）
    private var readPos = 0
    // 当前缓冲区中的有效数据量
    private var availableBytes = 0
    // 缓冲区总容量
    private val capacity = capacityBytes

    // 是否已关闭（停止写入后不再阻塞读取）
    @Volatile
    private var closed = false

    /**
     * 从 ByteBuffer 写入 PCM 数据到环形缓冲区。
     * 由 ExoPlayer 音频渲染线程调用。
     *
     * 如果缓冲区已满，丢弃最旧的数据（覆盖写入）。
     *
     * @param src 数据源 ByteBuffer，方法不会改变其 position
     * @return 实际写入的字节数
     */
    fun write(src: ByteBuffer): Int {
        val remaining = src.remaining()
        if (remaining == 0) return 0

        synchronized(lock) {
            if (closed) return 0

            val toWrite = remaining.coerceAtMost(capacity)
            val srcPos = src.position()

            // 如果数据量超过容量，跳过前面的部分（只保留最新的）
            val skipBytes = remaining - toWrite

            if (toWrite <= capacity - writePos) {
                // 一次性写入（不跨越缓冲区末尾）
                src.position(srcPos + skipBytes)
                src.get(buffer, writePos, toWrite)
            } else {
                // 分两次写入（跨越缓冲区末尾）
                val firstPart = capacity - writePos
                src.position(srcPos + skipBytes)
                src.get(buffer, writePos, firstPart)
                src.get(buffer, 0, toWrite - firstPart)
            }

            // 恢复原始 position
            src.position(srcPos)

            writePos = (writePos + toWrite) % capacity

            // 更新可用数据量
            if (toWrite > capacity - availableBytes) {
                // 缓冲区溢出，丢弃最旧数据
                val overflow = toWrite - (capacity - availableBytes)
                readPos = (readPos + overflow) % capacity
                availableBytes = capacity
            } else {
                availableBytes += toWrite
            }

            // 通知读取线程有新数据
            lock.notifyAll()

            return toWrite
        }
    }

    /**
     * 从环形缓冲区读取 PCM 数据。
     * 由 TCP 发送协程调用。
     *
     * 如果缓冲区为空，阻塞等待直到有数据或超时。
     *
     * @param dst 目标数组
     * @param timeoutMs 最大等待时间（毫秒），0 表示不等待
     * @return 实际读取的字节数，-1 表示缓冲区已关闭
     */
    fun read(dst: ByteArray, timeoutMs: Long = 100): Int {
        synchronized(lock) {
            // 等待数据可用
            if (availableBytes == 0) {
                if (closed) return -1
                if (timeoutMs <= 0) return 0

                try {
                    lock.wait(timeoutMs)
                } catch (_: InterruptedException) {
                    return if (closed) -1 else 0
                }

                if (availableBytes == 0) {
                    return if (closed) -1 else 0
                }
            }

            val toRead = availableBytes.coerceAtMost(dst.size)

            if (toRead <= capacity - readPos) {
                // 一次性读取（不跨越缓冲区末尾）
                System.arraycopy(buffer, readPos, dst, 0, toRead)
            } else {
                // 分两次读取（跨越缓冲区末尾）
                val firstPart = capacity - readPos
                System.arraycopy(buffer, readPos, dst, 0, firstPart)
                System.arraycopy(buffer, 0, dst, firstPart, toRead - firstPart)
            }

            readPos = (readPos + toRead) % capacity
            availableBytes -= toRead

            return toRead
        }
    }

    /**
     * 获取当前可用数据量（字节）
     */
    fun available(): Int {
        synchronized(lock) {
            return availableBytes
        }
    }

    /**
     * 清空缓冲区。在 seek 操作时调用。
     */
    fun flush() {
        synchronized(lock) {
            readPos = 0
            writePos = 0
            availableBytes = 0
            Log.d(TAG, "缓冲区已清空")
        }
    }

    /**
     * 关闭缓冲区，释放等待的线程。
     */
    fun close() {
        synchronized(lock) {
            closed = true
            lock.notifyAll()
            Log.d(TAG, "缓冲区已关闭")
        }
    }

    /**
     * 重置缓冲区为可使用状态。
     */
    fun reset() {
        synchronized(lock) {
            closed = false
            readPos = 0
            writePos = 0
            availableBytes = 0
            Log.d(TAG, "缓冲区已重置")
        }
    }

    /**
     * 缓冲区使用率（0.0 - 1.0）
     */
    fun usage(): Float {
        synchronized(lock) {
            return availableBytes.toFloat() / capacity
        }
    }
}
