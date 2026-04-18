package com.example.floatingscreencasting.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 日期时间工具类
 * 提供日期格式化和时间转换功能
 */
object DateUtils {

    /**
     * 格式化时间为HH:MM:SS格式
     */
    fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    }

    /**
     * 格式化时间为MM:SS格式（短时长）
     */
    fun formatShortTime(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }

    /**
     * 格式化时长（自动选择格式）
     */
    fun formatDuration(seconds: Long): String {
        return if (seconds >= 3600) {
            formatTime(seconds)
        } else {
            formatShortTime(seconds)
        }
    }

    /**
     * 解析时间字符串为秒数
     * 支持 HH:MM:SS 和 MM:SS 格式
     */
    fun parseTime(timeStr: String): Long {
        val parts = timeStr.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            2 -> parts[0] * 60L + parts[1]
            else -> 0L
        }
    }

    /**
     * 格式化日期时间
     */
    fun formatDateTime(timestamp: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 格式化为相对时间（如"刚刚"、"5分钟前"）
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> formatDateTime(timestamp, "yyyy-MM-dd")
        }
    }

    /**
     * 毫秒转秒
     */
    fun msToSeconds(ms: Long): Long {
        return ms / 1000
    }

    /**
     * 秒转毫秒
     */
    fun secondsToMs(seconds: Long): Long {
        return seconds * 1000
    }

    /**
     * 获取当前时间戳（秒）
     */
    fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis() / 1000
    }

    /**
     * 计算两个时间戳的差值（秒）
     */
    fun timeDiff(timestamp1: Long, timestamp2: Long): Long {
        return abs(timestamp1 - timestamp2)
    }

    /**
     * 检查时间是否在指定范围内
     */
    fun isTimeInRange(timestamp: Long, startTimestamp: Long, endTimestamp: Long): Boolean {
        return timestamp in startTimestamp..endTimestamp
    }

    /**
     * 格式化进度百分比
     */
    fun formatProgress(current: Long, total: Long): String {
        if (total == 0L) return "0%"
        val percentage = (current * 100 / total).toInt()
        return "$percentage%"
    }

    /**
     * 计算剩余时间（秒）
     */
    fun calculateRemainingTime(currentPosition: Long, totalDuration: Long): Long {
        return totalDuration - currentPosition
    }

    /**
     * 格式化剩余时间
     */
    fun formatRemainingTime(currentPosition: Long, totalDuration: Long): String {
        val remaining = calculateRemainingTime(currentPosition, totalDuration)
        return if (remaining > 0) {
            "剩余 ${formatDuration(remaining)}"
        } else {
            "已完成"
        }
    }

    /**
     * 获取今日日期字符串
     */
    fun getTodayDate(): String {
        return formatDateTime(System.currentTimeMillis(), "yyyy-MM-dd")
    }

    /**
     * 获取当前时间字符串
     */
    fun getCurrentTime(): String {
        return formatDateTime(System.currentTimeMillis(), "HH:mm:ss")
    }

    /**
     * 检查是否是同一天
     */
    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val date1 = formatDateTime(timestamp1, "yyyy-MM-dd")
        val date2 = formatDateTime(timestamp2, "yyyy-MM-dd")
        return date1 == date2
    }
}
