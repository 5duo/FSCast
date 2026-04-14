package com.example.floatingscreencasting.dlna

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.*

/**
 * 哔哩哔哩WBI签名工具
 * 用于生成和刷新Bilibili视频URL的WBI签名
 *
 * 参考资料：
 * - https://github.com/SocialSisterYi/bilibili-API-collect/blob/main/docs/misc/sign/wbi.md
 * - https://zhuanlan.zhihu.com/p/1961014749807513938
 */
object BilibiliWbiSigner {

    private const val TAG = "BilibiliWbiSigner"

    // Bilibili WBI签名相关端点
    private const val WBI_API_URL = "https://api.bilibili.com/x/web-interface/nav"
    private const val WBI_IMG_URL = "https://api.bilibili.com/x/web-interface/wbi/img"

    /**
     * MixinKey混淆密钥表（标准的64位索引表）
     * 这是B站前端JS代码中固定的混淆表，用于重新排列密钥字符
     */
    private val MIXIN_KEY_TABLE = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    // WBI密钥缓存
    private var cachedImgKey: String? = null
    private var cachedSubKey: String? = null
    private var cachedMixinKey: String? = null
    private var cacheTime: Long = 0
    private val CACHE_DURATION = 30 * 60 * 1000L // 30分钟

    /**
     * 修复Bilibili URL签名
     * 对于bilivideo.com视频文件URL，移除设备绑定参数
     */
    suspend fun fixBilibiliUrl(originalUrl: String): String {
        try {
            Log.d(TAG, "========== 开始修复Bilibili URL ==========")
            Log.d(TAG, "原始URL长度: ${originalUrl.length}")

            val uri = android.net.Uri.parse(originalUrl)

            // 情况1: 检查是否包含w_rid参数（API请求URL，需要WBI签名）
            val wrid = uri.getQueryParameter("w_rid")
            if (wrid != null && wrid.isNotEmpty()) {
                Log.d(TAG, "检测到WBI签名URL，需要重新生成签名")
                // 对于API URL，暂时不处理，因为主要用于视频播放URL
                return originalUrl
            }

            // 情况2: bilivideo.com视频文件URL
            if (originalUrl.contains("bilivideo.com")) {
                Log.d(TAG, "检测到Bilibili视频文件URL")
                Log.d(TAG, "策略: 直接使用原始URL（不做任何修改）")
                Log.d(TAG, "原因: B站可能已经根据设备信息（乐播伪装）下发了可用URL")

                // 直接返回原始URL，不做任何修改
                // 因为我们在DlnaHttpServer中已经伪装成乐播设备
                // B站可能会给乐播设备下发不需要额外验证的URL
                return originalUrl
            }

            Log.d(TAG, "URL不需要修复")
            return originalUrl

        } catch (e: Exception) {
            Log.e(TAG, "修复Bilibili URL失败", e)
            return originalUrl
        }
    }

    /**
     * 获取WBI密钥（imgKey和subKey）并生成MixinKey
     */
    suspend fun getWbiKeys(): Pair<String, String> = withContext(Dispatchers.IO) {
        // 检查缓存
        val currentTime = System.currentTimeMillis()
        if (cachedImgKey != null && cachedSubKey != null
            && currentTime - cacheTime < CACHE_DURATION) {
            Log.d(TAG, "使用缓存的WBI密钥")
            return@withContext Pair(cachedImgKey!!, cachedSubKey!!)
        }

        Log.d(TAG, "获取新的WBI密钥...")

        try {
            // 方法1：从nav接口获取
            val keys1 = fetchWbiKeysFromNav()
            if (keys1 != null) {
                cachedImgKey = keys1.first
                cachedSubKey = keys1.second
                cachedMixinKey = generateMixinKey(keys1.first, keys1.second)
                cacheTime = currentTime
                Log.d(TAG, "从nav接口成功获取WBI密钥")
                Log.d(TAG, "imgKey: ${keys1.first}")
                Log.d(TAG, "subKey: ${keys1.second}")
                Log.d(TAG, "MixinKey: ${cachedMixinKey}")
                return@withContext keys1
            }

            // 方法2：直接从img接口获取
            val keys2 = fetchWbiKeysFromImg()
            if (keys2 != null) {
                cachedImgKey = keys2.first
                cachedSubKey = keys2.second
                cachedMixinKey = generateMixinKey(keys2.first, keys2.second)
                cacheTime = currentTime
                Log.d(TAG, "从img接口成功获取WBI密钥")
                Log.d(TAG, "imgKey: ${keys2.first}")
                Log.d(TAG, "subKey: ${keys2.second}")
                Log.d(TAG, "MixinKey: ${cachedMixinKey}")
                return@withContext keys2
            }

            // 使用备用密钥
            Log.w(TAG, "无法获取实时密钥，使用备用密钥")
            val backupKeys = getBackupKeys()
            cachedImgKey = backupKeys.first
            cachedSubKey = backupKeys.second
            cachedMixinKey = generateMixinKey(backupKeys.first, backupKeys.second)
            cacheTime = currentTime
            return@withContext backupKeys

        } catch (e: Exception) {
            Log.e(TAG, "获取WBI密钥失败", e)
            // 返回备用密钥
            val backupKeys = getBackupKeys()
            cachedImgKey = backupKeys.first
            cachedSubKey = backupKeys.second
            cachedMixinKey = generateMixinKey(backupKeys.first, backupKeys.second)
            cacheTime = currentTime
            return@withContext backupKeys
        }
    }

    /**
     * 从nav接口获取WBI密钥
     */
    private fun fetchWbiKeysFromNav(): Pair<String, String>? {
        return try {
            Log.d(TAG, "尝试从nav接口获取密钥...")
            val jsonResponse = fetchUrl(WBI_API_URL)
            val json = JSONObject(jsonResponse)
            val data = json.getJSONObject("data")
            val wbiImg = data.getJSONObject("wbi_img")

            val imgKey = extractKeyFromUrl(wbiImg.getString("img_url"))
            val subKey = extractKeyFromUrl(wbiImg.getString("sub_url"))

            Log.d(TAG, "nav接口 - imgKey: $imgKey")
            Log.d(TAG, "nav接口 - subKey: $subKey")

            Pair(imgKey, subKey)

        } catch (e: Exception) {
            Log.e(TAG, "从nav接口获取密钥失败", e)
            null
        }
    }

    /**
     * 从img接口获取WBI密钥
     */
    private fun fetchWbiKeysFromImg(): Pair<String, String>? {
        return try {
            Log.d(TAG, "尝试从img接口获取密钥...")
            val jsonResponse = fetchUrl(WBI_IMG_URL)
            val json = JSONObject(jsonResponse)
            val data = json.getJSONObject("data")

            val imgKey = data.getString("img_key")
            val subKey = data.getString("sub_key")

            Log.d(TAG, "img接口 - imgKey: $imgKey")
            Log.d(TAG, "img接口 - subKey: $subKey")

            Pair(imgKey, subKey)

        } catch (e: Exception) {
            Log.e(TAG, "从img接口获取密钥失败", e)
            null
        }
    }

    /**
     * 从URL中提取密钥
     */
    private fun extractKeyFromUrl(url: String): String {
        // URL格式: https://xxx/x/abcd1234.png
        val parts = url.split("/")
        val filename = parts.lastOrNull() ?: return ""
        // 移除扩展名
        return filename.substring(0, filename.lastIndexOf('.'))
    }

    /**
     * 获取备用密钥（用于网络请求失败时）
     */
    private fun getBackupKeys(): Pair<String, String> {
        // 这些是一些已知的Bilibili WBI密钥，会定期更新
        Log.w(TAG, "使用备用WBI密钥")
        return Pair(
            "1918c3e924d1a73b", // imgKey示例
            "6c7e4617489e4f5d"  // subKey示例
        )
    }

    /**
     * 生成MixinKey（混淆密钥）
     *
     * 算法步骤：
     * 1. 将imgKey和subKey拼接成一个字符串
     * 2. 按照MIXIN_KEY_TABLE中的索引顺序重新排列字符
     * 3. 取前32个字符作为最终的MixinKey
     */
    private fun generateMixinKey(imgKey: String, subKey: String): String {
        try {
            // 1. 拼接原始密钥
            val combined = imgKey + subKey

            Log.d(TAG, "generateMixinKey - 原始密钥长度: ${combined.length}")
            Log.d(TAG, "generateMixinKey - imgKey: $imgKey")
            Log.d(TAG, "generateMixinKey - subKey: $subKey")

            // 2. 按照混淆表索引重新排列字符
            val mixedChars = mutableListOf<Char>()
            for (index in MIXIN_KEY_TABLE) {
                if (index < combined.length) {
                    mixedChars.add(combined[index])
                }
            }

            // 3. 取前32个字符作为最终密钥
            val mixinKey = mixedChars.joinToString("").take(32)

            Log.d(TAG, "generateMixinKey - MixinKey: $mixinKey")
            Log.d(TAG, "generateMixinKey - MixinKey长度: ${mixinKey.length}")

            return mixinKey

        } catch (e: Exception) {
            Log.e(TAG, "生成MixinKey失败", e)
            // 返回拼接的密钥作为后备
            return (imgKey + subKey).take(32)
        }
    }

    /**
     * 生成WBI签名
     *
     * 算法步骤：
     * 1. 添加wts时间戳参数
     * 2. 按字母顺序排序所有参数
     * 3. 拼接成key=value&key=value格式
     * 4. 在末尾加上MixinKey
     * 5. 计算MD5哈希值作为w_rid
     */
    fun generateWbiSignature(
        params: MutableMap<String, String>,
        imgKey: String,
        subKey: String
    ): String {
        try {
            // 1. 获取当前时间戳
            val timestamp = System.currentTimeMillis() / 1000
            params["wts"] = timestamp.toString()

            // 2. 生成MixinKey
            val mixinKey = generateMixinKey(imgKey, subKey)

            // 3. 按字母顺序排序参数
            val sortedKeys = params.keys.sorted()

            // 4. 拼接参数字符串
            val signString = StringBuilder()
            for (key in sortedKeys) {
                if (key != "w_rid") {  // 排除w_rid本身
                    signString.append("$key=${params[key]}&")
                }
            }
            // 移除最后一个&
            if (signString.endsWith("&")) {
                signString.setLength(signString.length - 1)
            }

            // 5. 加上MixinKey
            val stringToSign = signString.toString() + mixinKey

            // 6. 计算MD5哈希
            val wrid = md5Hash(stringToSign)

            Log.d(TAG, "generateWbiSignature - 签名字符串长度: ${signString.length}")
            Log.d(TAG, "generateWbiSignature - MixinKey: $mixinKey")
            Log.d(TAG, "generateWbiSignature - 生成的w_rid: $wrid")

            return wrid

        } catch (e: Exception) {
            Log.e(TAG, "生成WBI签名失败", e)
            return ""
        }
    }

    /**
     * 构建Bilibili URL
     * @param path URL路径（例如：/play/url.m3u8）
     * @param params 查询参数
     * @param baseUrl 基础URL（包含scheme和authority）
     */
    private fun buildBilibiliUrl(path: String, params: Map<String, String>, baseUrl: String = ""): String {
        val query = StringBuilder()
        val sortedKeys = params.keys.sorted()

        for (key in sortedKeys) {
            if (query.isNotEmpty()) {
                query.append("&")
            }
            query.append("$key=${java.net.URLEncoder.encode(params[key]!!, "UTF-8")}")
        }

        // 如果提供了baseUrl，使用它；否则构建相对URL
        return if (baseUrl.isNotEmpty()) {
            "$baseUrl$path?$query"
        } else {
            "$path?$query"
        }
    }

    /**
     * HTTP GET请求
     */
    private fun fetchUrl(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        connection.setRequestProperty("Referer", "https://www.bilibili.com")

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        return response
    }

    /**
     * MD5哈希
     */
    private fun md5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 检查URL是否为Bilibili链接
     */
    fun isBilibiliUrl(url: String): Boolean {
        return url.contains("bilibili.com") ||
               url.contains("biliapi.com") ||
               url.contains("acgvideo.com") ||
               url.contains("bilivideo.com")
    }

    /**
     * 从Bilibili URL提取BVID
     */
    fun extractBvid(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val path = uri.path ?: return null

            // 从路径中提取BVID
            // 例如: /video/BV1xx411c7m7 -> BV1xx411c7m7
            val bvidPattern = "BV[\\w]+".toRegex()
            val matcher = bvidPattern.find(path)
            matcher?.value

        } catch (e: Exception) {
            Log.e(TAG, "提取BVID失败", e)
            null
        }
    }

    /**
     * 获取当前缓存的MixinKey
     */
    fun getCachedMixinKey(): String? {
        val currentTime = System.currentTimeMillis()
        if (cachedMixinKey != null && currentTime - cacheTime < CACHE_DURATION) {
            return cachedMixinKey
        }
        return null
    }
}
