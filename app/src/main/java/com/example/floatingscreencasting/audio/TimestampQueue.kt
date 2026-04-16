package com.example.floatingscreencasting.audio

import android.util.Log

/**
 * 时间戳队列，与 PcmRingBuffer 配合使用。
 *
 * 每个 PCM 数据块都有一个对应的时间戳（presentationTimeUs）。
 * 当从 PcmRingBuffer 读取 PCM 数据时，也从此队列读取对应的时间戳。
 */
class TimestampQueue {
    companion object {
        private const val TAG = "TimestampQueue"
        private const val MAX_QUEUE_SIZE = 1000  // 最多存储 1000 个时间戳
    }

    private val queue = ArrayDeque<Pair<Long, Int>>()  // (时间戳, 字节数)
    private val lock = Any()

    /**
     * 写入时间戳
     * @param timestamp 时间戳（presentationTimeUs）
     * @param bytes 对应的 PCM 数据字节数
     */
    fun write(timestamp: Long, bytes: Int) {
        synchronized(lock) {
            queue.addLast(Pair(timestamp, bytes))

            // 限制队列大小，避免内存泄漏
            while (queue.size > MAX_QUEUE_SIZE) {
                queue.removeFirst()
            }
        }
    }

    /**
     * 读取时间戳（对应从 PcmRingBuffer 读取的 PCM 数据）
     * @param bytes 读取的 PCM 数据字节数
     * @return 对应的时间戳，如果没有时间戳则返回 0
     */
    fun read(bytes: Int): Long {
        synchronized(lock) {
            if (queue.isEmpty()) return 0

            var totalBytes = 0
            var lastTimestamp = 0L

            // 累积字节直到达到或超过读取的字节数
            while (queue.isNotEmpty() && totalBytes < bytes) {
                val (timestamp, chunkBytes) = queue.removeFirst()
                lastTimestamp = timestamp
                totalBytes += chunkBytes
            }

            return lastTimestamp
        }
    }

    /**
     * 清空队列
     */
    fun clear() {
        synchronized(lock) {
            queue.clear()
            Log.d(TAG, "时间戳队列已清空")
        }
    }

    /**
     * 获取队列大小
     */
    fun size(): Int {
        synchronized(lock) {
            return queue.size
        }
    }
}
