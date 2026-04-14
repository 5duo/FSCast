package com.example.floatingscreencasting.ui

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 日志收集器
 * 捕获应用日志并显示在UI上
 */
object LogCollector {

    private const val TAG = "LogCollector"
    private const val MAX_LOG_LINES = 500

    // 日志队列
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private var isCollecting = false
    private var collectThread: Thread? = null

    // 日志监听器
    private val listeners = mutableSetOf<LogListener>()

    data class LogEntry(
        val timestamp: Long,
        val level: Int,
        val tag: String,
        val message: String
    ) {
        fun getFormattedText(): String {
            val time = java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date(timestamp))
            val levelStr = when (level) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                else -> "?"
            }
            return "$time $levelStr/$tag: $message\n"
        }
    }

    interface LogListener {
        fun onNewLog(entry: LogEntry)
    }

    /**
     * 添加日志监听器
     */
    fun addLogListener(listener: LogListener) {
        listeners.add(listener)
    }

    /**
     * 移除日志监听器
     */
    fun removeLogListener(listener: LogListener) {
        listeners.remove(listener)
    }

    /**
     * 开始收集日志
     */
    fun startCollecting() {
        if (isCollecting) return

        isCollecting = true
        collectThread = Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-c"))
                process.waitFor()

                val process2 = Runtime.getRuntime().exec(arrayOf(
                    "logcat",
                    "-v", "time",
                    "*:D"
                ))

                val reader = BufferedReader(InputStreamReader(process2.inputStream))
                var line: String?

                while (isCollecting) {
                    line = reader.readLine()
                    if (line != null) {
                        parseAndAddLog(line)
                    }
                }

                reader.close()
                process2.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "日志收集失败", e)
            }
        }.apply { start() }

        Log.d(TAG, "日志收集已启动")
    }

    /**
     * 停止收集日志
     */
    fun stopCollecting() {
        isCollecting = false
        collectThread?.interrupt()
        collectThread = null
        Log.d(TAG, "日志收集已停止")
    }

    /**
     * 解析日志行并添加到队列
     */
    private fun parseAndAddLog(line: String) {
        try {
            // 格式: MM-DD HH:mm:ss.mmm LEVEL/TAG: message
            val regex = """(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\w+)/([\w.]+):\s+(.+)""".toRegex()
            val match = regex.find(line) ?: return

            val (timeStr, levelStr, tag, message) = match.destructured

            // 解析时间
            val timestamp = try {
                val sdf = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS")
                sdf.parse(timeStr)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            // 解析日志级别
            val level = when (levelStr) {
                "V" -> Log.VERBOSE
                "D" -> Log.DEBUG
                "I" -> Log.INFO
                "W" -> Log.WARN
                "E" -> Log.ERROR
                else -> Log.DEBUG
            }

            // 过滤不需要的日志
            if (shouldFilterLog(tag, message)) {
                return
            }

            val entry = LogEntry(timestamp, level, tag, message)

            // 添加到队列
            logQueue.offer(entry)

            // 限制队列大小
            while (logQueue.size > MAX_LOG_LINES) {
                logQueue.poll()
            }

            // 通知监听器
            listeners.forEach { listener ->
                try {
                    listener.onNewLog(entry)
                } catch (e: Exception) {
                    Log.e(TAG, "通知监听器失败", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "解析日志失败: $line", e)
        }
    }

    /**
     * 判断是否应该过滤此日志
     */
    private fun shouldFilterLog(tag: String, message: String): Boolean {
        // 过滤掉过于频繁的日志
        val filteredTags = listOf(
            "OpenGLRenderer",
            "hwaps",
            "Surface",
            "GraphicBuffer",
            "EGL_emulation"
        )

        if (tag in filteredTags) return true

        // 过滤掉包含特定内容的日志
        val filteredContents = listOf(
            "Do not keep the activity",
            "is not valid, is your activity running?"
        )

        for (content in filteredContents) {
            if (message.contains(content)) return true
        }

        return false
    }

    /**
     * 获取所有日志
     */
    fun getAllLogs(): List<LogEntry> {
        return logQueue.toList()
    }

    /**
     * 清空日志
     */
    fun clearLogs() {
        logQueue.clear()
        Log.d(TAG, "日志已清空")
    }

    /**
     * 添加手动日志（用于显示应用内部状态）
     */
    fun addManualLog(level: Int, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        logQueue.offer(entry)
        listeners.forEach { it.onNewLog(entry) }
    }
}
