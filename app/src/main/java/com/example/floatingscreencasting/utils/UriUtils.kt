package com.example.floatingscreencasting.utils

import android.util.Log
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * URI相关工具类
 * 提供URI解析和提取功能
 */
object UriUtils {
    private const val TAG = "UriUtils"

    /**
     * 从URI中提取视频标题
     * 根据URL特征识别视频平台
     *
     * @param uri 视频URI
     * @return 视频标题/平台名称
     */
    fun extractTitleFromUri(uri: String): String {
        return when {
            uri.contains("bilibili") -> "哔哩哔哩"
            uri.contains("iqiyi") -> "爱奇艺"
            uri.contains("v.qq.com") -> "腾讯视频"
            uri.contains("youku") -> "优酷"
            uri.contains("mgtv") -> "芒果TV"
            else -> {
                // 尝试从URL路径中提取标题
                try {
                    val segments = uri.split("/")
                    val lastSegment = segments.lastOrNull() ?: ""
                    if (lastSegment.isNotEmpty()) {
                        // 限制标题长度，避免过长
                        lastSegment.substring(0, lastSegment.length.coerceAtMost(50))
                    } else {
                        "在线视频"
                    }
                } catch (e: Exception) {
                    "在线视频"
                }
            }
        }
    }

    /**
     * 从URI中提取Referer
     * 根据URL特征返回对应的Referer
     *
     * @param uri 视频URI
     * @return Referer URL
     */
    fun extractRefererFromUri(uri: String): String {
        return when {
            uri.contains("bilibili.com") || uri.contains("bilivideo.com") || uri.contains("acgvideo.com") -> "https://www.bilibili.com"
            uri.contains("iqiyi.com") || uri.contains("qiyi.com") -> "https://www.iqiyi.com"
            uri.contains("v.qq.com") -> "https://v.qq.com"
            uri.contains("youku.com") -> "https://www.youku.com"
            uri.contains("mgtv.com") -> "https://www.mgtv.com"
            else -> ""
        }
    }

    /**
     * 从URI中提取平台类型
     *
     * @param uri 视频URI
     * @return 平台类型
     */
    fun extractPlatformFromUri(uri: String): VideoPlatform {
        return when {
            uri.contains("bilibili") -> VideoPlatform.BILIBILI
            uri.contains("iqiyi") || uri.contains("qiyi") -> VideoPlatform.IQIYI
            uri.contains("v.qq.com") -> VideoPlatform.TENCENT
            uri.contains("youku") -> VideoPlatform.YOUKU
            uri.contains("mgtv") -> VideoPlatform.MANGUO
            else -> VideoPlatform.UNKNOWN
        }
    }

    /**
     * 视频平台枚举
     */
    enum class VideoPlatform {
        BILIBILI,    // 哔哩哔哩
        IQIYI,       // 爱奇艺
        TENCENT,     // 腾讯视频
        YOUKU,       // 优酷
        MANGUO,      // 芒果TV
        UNKNOWN      // 未知平台
    }

    /**
     * 从Referer或URL中提取B站视频ID (BV号或AV号)
     * @param referer Referer URL或任意包含B站视频链接的字符串
     * @return Pair(BVID/AV号, 类型) 类型为"bv"或"av"，如果未找到则返回null
     */
    fun extractBilibiliVideoId(referer: String?): Pair<String, String>? {
        if (referer.isNullOrBlank()) return null

        try {
            // URL解码
            val decoded = URLDecoder.decode(referer, StandardCharsets.UTF_8.toString())

            // 匹配BV号: BVxxxxxxxxxx (12位)
            val bvPattern = """(BV[a-zA-Z0-9]{10,12})""".toRegex()
            val bvMatch = bvPattern.find(decoded)
            if (bvMatch != null) {
                val bvid = bvMatch.groupValues[1]
                Log.d(TAG, "提取到B站BV号: $bvid")
                return Pair(bvid, "bv")
            }

            // 匹配AV号: avxxxxxxxx (纯数字)
            val avPattern = """(av\d+)|(/video/(\d+))""".toRegex()
            val avMatch = avPattern.find(decoded)
            if (avMatch != null) {
                val avid = avMatch.groupValues[1].ifBlank { avMatch.groupValues[3] }
                if (avid.isNotBlank()) {
                    Log.d(TAG, "提取到B站AV号: $avid")
                    return Pair(avid, "av")
                }
            }

            Log.d(TAG, "未能从Referer中提取B站视频ID: $decoded")
        } catch (e: Exception) {
            Log.e(TAG, "提取B站视频ID失败", e)
        }

        return null
    }

    /**
     * 从B站视频流URL中提取可能的视频标识信息
     * B站的流URL格式类似: https://xxx.bilivideo.com/xxx.m3u8?params=...
     * 有时会在参数中包含session或video标识
     * @param streamUrl B站视频流URL
     * @return 可能包含的视频标识信息
     */
    fun extractBilibiliStreamInfo(streamUrl: String): Map<String, String> {
        val info = mutableMapOf<String, String>()

        try {
            val url = URLDecoder.decode(streamUrl, StandardCharsets.UTF_8.toString())

            // 检查是否是B站流
            if (!url.contains("bilivideo.com") && !url.contains("acgvideo.com")) {
                return info
            }

            // 尝试从URL参数中提取信息
            val params = url.split("?").getOrNull(1)?.split("&") ?: emptyList()

            for (param in params) {
                val parts = param.split("=")
                if (parts.size == 2) {
                    val key = parts[0].lowercase()
                    val value = parts[1]

                    // 保存可能有用的参数
                    when (key) {
                        "session", "sid" -> info["session"] = value
                        "quality" -> info["quality"] = value
                        "fnval", "fnver" -> info["format"] = value
                    }
                }
            }

            if (info.isNotEmpty()) {
                Log.d(TAG, "从B站流URL中提取信息: $info")
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取B站流信息失败", e)
        }

        return info
    }
}
