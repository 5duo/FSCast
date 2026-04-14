package com.example.floatingscreencasting.history

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 播放历史记录管理器
 * 保存用户的播放历史，支持继续观看功能
 */
class PlaybackHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "playback_history"
        private const val KEY_LAST_PLAYED = "last_played"
        private const val KEY_HISTORY_LIST = "history_list"
        private const val MAX_HISTORY_SIZE = 20 // 最多保存20条历史记录

        @Volatile
        private var instance: PlaybackHistoryManager? = null

        fun getInstance(context: Context): PlaybackHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 播放记录数据类
     */
    @Serializable
    data class PlaybackRecord(
        val uri: String,
        val title: String,
        val positionMs: Long,
        val durationMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        /**
         * 获取播放进度百分比
         */
        fun getProgressPercent(): Int {
            return if (durationMs > 0) {
                ((positionMs.toFloat() / durationMs) * 100).toInt()
            } else 0
        }

        /**
         * 格式化时间戳
         */
        fun getFormattedTime(): String {
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000 -> "刚刚"
                diff < 3600_000 -> "${diff / 60_000}分钟前"
                diff < 86400_000 -> "${diff / 3600_000}小时前"
                else -> "${diff / 86400_000}天前"
            }
        }
    }

    /**
     * 保存播放记录
     */
    fun savePlayback(
        uri: String,
        title: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val record = PlaybackRecord(
            uri = uri,
            title = title,
            positionMs = positionMs,
            durationMs = durationMs
        )

        // 保存为最后播放
        prefs.edit().putString(KEY_LAST_PLAYED, json.encodeToString(record)).apply()

        // 更新历史列表
        val history = getHistoryList().toMutableList()

        // 移除相同URI的旧记录
        history.removeAll { it.uri == uri }

        // 添加新记录到开头
        history.add(0, record)

        // 限制历史记录数量
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }

        prefs.edit().putString(KEY_HISTORY_LIST, json.encodeToString(history)).apply()

        android.util.Log.d("PlaybackHistory", "保存播放记录: $title, 进度: ${positionMs / 1000}s")
    }

    /**
     * 更新播放进度
     */
    fun updateProgress(uri: String, positionMs: Long, durationMs: Long) {
        val lastPlayed = getLastPlayed() ?: return

        if (lastPlayed.uri == uri) {
            savePlayback(uri, lastPlayed.title, positionMs, durationMs)
        }
    }

    /**
     * 获取最后播放的记录
     */
    fun getLastPlayed(): PlaybackRecord? {
        val jsonStr = prefs.getString(KEY_LAST_PLAYED, null)
        return if (jsonStr != null) {
            try {
                json.decodeFromString<PlaybackRecord>(jsonStr)
            } catch (e: Exception) {
                android.util.Log.e("PlaybackHistory", "解析最后播放记录失败", e)
                null
            }
        } else null
    }

    /**
     * 获取历史记录列表
     */
    fun getHistoryList(): List<PlaybackRecord> {
        val jsonStr = prefs.getString(KEY_HISTORY_LIST, "[]")
        return try {
            json.decodeFromString<List<PlaybackRecord>>(jsonStr ?: "[]")
        } catch (e: Exception) {
            android.util.Log.e("PlaybackHistory", "解析历史记录列表失败", e)
            emptyList()
        }
    }

    /**
     * 清空所有历史记录
     */
    fun clearHistory() {
        prefs.edit()
            .remove(KEY_LAST_PLAYED)
            .remove(KEY_HISTORY_LIST)
            .apply()
        android.util.Log.d("PlaybackHistory", "历史记录已清空")
    }

    /**
     * 删除指定的历史记录
     */
    fun deleteRecord(uri: String) {
        val history = getHistoryList().toMutableList()
        history.removeAll { it.uri == uri }

        prefs.edit().putString(KEY_HISTORY_LIST, json.encodeToString(history)).apply()

        // 如果删除的是最后播放的记录，也要清除
        val lastPlayed = getLastPlayed()
        if (lastPlayed?.uri == uri) {
            prefs.edit().remove(KEY_LAST_PLAYED).apply()
        }
    }

    /**
     * 检查是否有继续观看的内容
     */
    fun hasContinueWatching(): Boolean {
        val lastPlayed = getLastPlayed() ?: return false

        // 如果播放进度超过5%且未完成，则认为可以继续观看
        val progressPercent = lastPlayed.getProgressPercent()
        return progressPercent > 5 && progressPercent < 95
    }
}
