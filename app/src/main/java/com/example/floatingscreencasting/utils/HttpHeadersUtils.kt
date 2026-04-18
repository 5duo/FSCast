package com.example.floatingscreencasting.utils

/**
 * HTTP请求头工具类
 * 为不同视频平台提供合适的请求头
 */
object HttpHeadersUtils {

    // User-Agent 常量
    private const val MI_TV_USER_AGENT = "MiTV/1.0 (Linux;Android 12) MI_TV_4"
    private const val FSCAST_USER_AGENT = "Mozilla/5.0 (Linux; Android 12) FSCast/1.0"
    private const val BILIBILI_USER_AGENT = ("Mozilla/5.0 (Linux; Android 9; MiTV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/74.0.3729.136 Mobile Safari/537.36 " +
            "BiliApp/1.0 Lelink/1.0")

    /**
     * 根据URI获取对应的HTTP请求头
     * 包括Referer和User-Agent
     *
     * @param uri 视频URI
     * @return HTTP请求头Map
     */
    fun getHeadersForUri(uri: String): Map<String, String> {
        return when {
            // Bilibili - 需要特殊的请求头
            uri.contains("bilibili") || uri.contains("bilivideo.com") || uri.contains("acgvideo.com") -> mapOf(
                "Referer" to "https://www.bilibili.com/",
                "Origin" to "https://www.bilibili.com",
                "User-Agent" to BILIBILI_USER_AGENT,
                "Accept" to "*/*",
                "Accept-Language" to "zh-CN,zh;q=0.9",
                "Connection" to "keep-alive",
                "X-Lelink-XML" to "1",
                "X-Real-IP" to "127.0.0.1"
            )

            // 爱奇艺
            uri.contains("iqiyi") || uri.contains("qiyi.com") -> mapOf(
                "Referer" to "https://www.iqiyi.com/",
                "User-Agent" to MI_TV_USER_AGENT
            )

            // 腾讯视频
            uri.contains("v.qq.com") -> mapOf(
                "Referer" to "https://v.qq.com/",
                "User-Agent" to MI_TV_USER_AGENT
            )

            // 优酷
            uri.contains("youku") -> mapOf(
                "Referer" to "https://www.youku.com/",
                "User-Agent" to MI_TV_USER_AGENT
            )

            // 芒果TV
            uri.contains("mgtv") -> mapOf(
                "User-Agent" to MI_TV_USER_AGENT
            )

            // 默认请求头
            else -> mapOf(
                "User-Agent" to FSCAST_USER_AGENT,
                "Accept" to "*/*"
            )
        }
    }

    /**
     * 获取默认的HTTP数据源配置
     *
     * @return 配置参数Map
     */
    fun getDefaultHttpConfig(): Map<String, Any> {
        return mapOf(
            "connect_timeout_ms" to 30_000L,
            "read_timeout_ms" to 30_000L,
            "allow_cross_protocol_redirects" to true,
            "user_agent" to FSCAST_USER_AGENT
        )
    }

    /**
     * 创建HTTP数据源工厂的配置
     *
     * @return 配置Pair（超时时间, User-Agent）
     */
    fun createHttpDataSourceConfig(): Pair<Long, String> {
        return Pair(30_000L, FSCAST_USER_AGENT)
    }
}
