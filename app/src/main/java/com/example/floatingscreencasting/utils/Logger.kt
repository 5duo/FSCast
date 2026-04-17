package com.example.floatingscreencasting.utils

import android.util.Log

/**
 * 统一的日志工具类
 * 提供带分隔符的日志输出，方便调试
 */
object Logger {

    private const val DIVIDER = "========================================"
    private const val DIVIDER_LENGTH = 40

    /**
     * 输出带分隔符的INFO日志
     * @param tag 日志标签
     * @param message 日志消息
     * @param withDivider 是否在消息前后添加分隔符
     */
    fun info(tag: String, message: String, withDivider: Boolean = false) {
        if (withDivider) {
            Log.i(tag, DIVIDER)
        }
        Log.i(tag, message)
        if (withDivider) {
            Log.i(tag, DIVIDER)
        }
    }

    /**
     * 输出带分隔符的ERROR日志
     * @param tag 日志标签
     * @param message 日志消息
     * @param withDivider 是否在消息前后添加分隔符
     */
    fun error(tag: String, message: String, withDivider: Boolean = false) {
        if (withDivider) {
            Log.e(tag, DIVIDER)
        }
        Log.e(tag, message)
        if (withDivider) {
            Log.e(tag, DIVIDER)
        }
    }

    /**
     * 输出带分隔符的WARN日志
     * @param tag 日志标签
     * @param message 日志消息
     * @param withDivider 是否在消息前后添加分隔符
     */
    fun warn(tag: String, message: String, withDivider: Boolean = false) {
        if (withDivider) {
            Log.w(tag, DIVIDER)
        }
        Log.w(tag, message)
        if (withDivider) {
            Log.w(tag, DIVIDER)
        }
    }

    /**
     * 输出带分隔符的DEBUG日志
     * @param tag 日志标签
     * @param message 日志消息
     * @param withDivider 是否在消息前后添加分隔符
     */
    fun debug(tag: String, message: String, withDivider: Boolean = false) {
        if (withDivider) {
            Log.d(tag, DIVIDER)
        }
        Log.d(tag, message)
        if (withDivider) {
            Log.d(tag, DIVIDER)
        }
    }

    /**
     * 输出完整的错误堆栈
     * @param tag 日志标签
     * @param message 日志消息
     * @param throwable 异常对象
     */
    fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, DIVIDER)
        Log.e(tag, message)
        throwable?.let { Log.e(tag, "异常详情: ", it) }
        Log.e(tag, DIVIDER)
    }

    /**
     * 输出步骤日志，用于跟踪流程
     * @param tag 日志标签
     * @param step 步骤名称
     * @param details 详细信息
     */
    fun step(tag: String, step: String, details: String = "") {
        Log.i(tag, "[$step] ${if (details.isNotEmpty()) details else ""}")
    }
}
