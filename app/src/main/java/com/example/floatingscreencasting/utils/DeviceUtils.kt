package com.example.floatingscreencasting.utils

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager
import java.io.BufferedReader
import java.io.FileReader

/**
 * 设备信息工具类
 * 提供设备硬件和系统信息获取功能
 */
object DeviceUtils {

    /**
     * 获取设备名称
     */
    fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /**
     * 获取Android版本
     */
    fun getAndroidVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    /**
     * 获取可用内存大小（MB）
     */
    fun getAvailableMemory(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024)
    }

    /**
     * 获取总内存大小（MB）
     */
    fun getTotalMemory(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024 * 1024)
    }

    /**
     * 检查是否是低内存设备
     */
    fun isLowMemoryDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }

    /**
     * 获取屏幕分辨率
     */
    fun getScreenResolution(context: Context): String {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display ?: windowManager.defaultDisplay
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics)
        return "${metrics.widthPixels}x${metrics.heightPixels}"
    }

    /**
     * 获取屏幕密度
     */
    fun getScreenDensity(context: Context): Float {
        return context.resources.displayMetrics.density
    }

    /**
     * 获取CPU核心数
     */
    fun getCpuCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    /**
     * 获取CPU使用率
     */
    fun getCpuUsage(): Float {
        try {
            val reader = BufferedReader(FileReader("/proc/stat"))
            val line = reader.readLine()
            reader.close()

            val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            if (parts.size < 8) return 0f

            val idle = parts[4].toLong()
            val total = (parts[1].toLong() + parts[2].toLong() + parts[3].toLong() +
                    parts[4].toLong() + parts[5].toLong() + parts[6].toLong() + parts[7].toLong())

            Thread.sleep(100)

            val reader2 = BufferedReader(FileReader("/proc/stat"))
            val line2 = reader2.readLine()
            reader2.close()

            val parts2 = line2.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            if (parts2.size < 8) return 0f

            val idle2 = parts2[4].toLong()
            val total2 = (parts2[1].toLong() + parts2[2].toLong() + parts2[3].toLong() +
                    parts2[4].toLong() + parts2[5].toLong() + parts2[6].toLong() + parts2[7].toLong())

            val totalDiff = total2 - total
            val idleDiff = idle2 - idle

            if (totalDiff == 0L) return 0f

            return 100f * (totalDiff - idleDiff).toFloat() / totalDiff.toFloat()
        } catch (e: Exception) {
            Log.e("DeviceUtils", "获取CPU使用率失败", e)
            return 0f
        }
    }

    /**
     * 检查是否是模拟器
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT)
    }

    /**
     * 获取设备唯一标识
     */
    fun getDeviceId(): String {
        return Build.BOARD + Build.BRAND + Build.DEVICE + Build.HARDWARE + Build.ID +
                Build.MODEL + Build.PRODUCT + Build.SERIAL
    }

    /**
     * 检查是否支持多窗口
     */
    fun supportsMultiWindow(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    /**
     * 检查是否是平板
     */
    fun isTablet(context: Context): Boolean {
        val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }
}
