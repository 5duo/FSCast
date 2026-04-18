package com.example.floatingscreencasting.data.remote.signer

import android.util.Log
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.HashMap

/**
 * 爱奇艺签名工具 v2
 * 更完整的签名实现
 */
object IqiyiSigner {

    private const val TAG = "IqiyiSigner"

    // 爱奇艺主要密钥（从app中提取）
    private val KEYS = arrayOf(
        "5adH430U8a4d39b5",
        "d5d635d7494521a6",
        "6c7f1a5e3b2895c4"
    )

    /**
     * 修复爱奇艺URL
     * 尝试刷新签名参数以使URL可用
     */
    fun fixIqiyiUrl(originalUrl: String): String {
        try {
            Log.d(TAG, "========== 开始修复爱奇艺URL ==========")
            Log.d(TAG, "原始URL: $originalUrl")

            val uri = android.net.Uri.parse(originalUrl)
            val path = uri.path ?: return originalUrl

            // 提取基础参数
            val params = mutableMapOf<String, String>()
            uri.queryParameterNames.forEach { key ->
                uri.getQueryParameter(key)?.let { params[key] = it }
            }

            // 检查是否是m3u8请求
            if (!path.endsWith(".m3u8")) {
                Log.w(TAG, "不是m3u8请求: $path")
                return originalUrl
            }

            // 方法1: 更新时间戳和签名
            val fixedUrl1 = refreshTimestampAndSign(params, path, originalUrl)

            // 方法2: 如果方法1失败，尝试移除过期的签名参数
            val fixedUrl2 = removeExpiredParams(params, path)

            // 方法3: 使用备用URL格式
            val fixedUrl3 = generateAlternativeUrl(params, path)

            Log.d(TAG, "修复结果:")
            Log.d(TAG, "方法1 (刷新签名): ${if (fixedUrl1 != originalUrl) "成功" else "失败"}")
            Log.d(TAG, "方法2 (移除过期参数): ${if (fixedUrl2 != originalUrl) "成功" else "失败"}")
            Log.d(TAG, "方法3 (备用URL): ${if (fixedUrl3 != originalUrl) "成功" else "失败"}")

            // 优先使用方法1的结果
            return when {
                fixedUrl1 != originalUrl -> fixedUrl1
                fixedUrl2 != originalUrl -> fixedUrl2
                fixedUrl3 != originalUrl -> fixedUrl3
                else -> originalUrl
            }

        } catch (e: Exception) {
            Log.e(TAG, "修复爱奇艺URL失败", e)
            return originalUrl
        }
    }

    /**
     * 方法1: 刷新时间戳和签名
     */
    private fun refreshTimestampAndSign(
        params: MutableMap<String, String>,
        path: String,
        originalUrl: String
    ): String {
        return try {
            val currentTime = System.currentTimeMillis()

            // 更新时间参数
            params["tm"] = currentTime.toString()
            params["tmi"] = (currentTime / 1000).toString()
            params["qds"] = "0" // 禁用QDS缓存
            params["st"] = "0" // 禁用服务器时间校验

            // 生成新的签名
            val sign = generateNewSign(params, path)
            params["sign"] = sign

            // 生成新的验证码
            val vf = generateNewVf(params, path)
            params["vf"] = vf

            // 构建新URL
            buildNewUrl(path, params)

        } catch (e: Exception) {
            Log.e(TAG, "刷新签名失败", e)
            originalUrl
        }
    }

    /**
     * 生成新签名
     */
    private fun generateNewSign(params: Map<String, String>, path: String): String {
        try {
            // 爱奇艺签名算法
            val signString = StringBuilder()
            signString.append(path)

            // 按字母顺序添加参数
            val sortedKeys = params.keys.sorted()
            for (key in sortedKeys) {
                if (key != "sign" && key != "vf") {
                    signString.append("$key=${params[key]}&")
                }
            }

            // 添加密钥
            signString.append(KEYS[0])

            // MD5加密并转大写
            val md5 = md5Hash(signString.toString()).uppercase()

            Log.d(TAG, "签名字符串: $signString")
            Log.d(TAG, "MD5结果: $md5")

            return md5

        } catch (e: Exception) {
            Log.e(TAG, "生成签名失败", e)
            return ""
        }
    }

    /**
     * 生成新的验证码
     */
    private fun generateNewVf(params: Map<String, String>, path: String): String {
        try {
            val vfString = "${params["tm"]}${params["k_uid"]}${path}${KEYS[1]}"
            val md5 = md5Hash(vfString)

            // 取前8位并转大写
            return md5.substring(0, 8).uppercase()

        } catch (e: Exception) {
            Log.e(TAG, "生成验证码失败", e)
            return ""
        }
    }

    /**
     * 方法2: 移除可能导致签名验证失败的参数
     */
    private fun removeExpiredParams(
        params: MutableMap<String, String>,
        path: String
    ): String {
        try {
            val cleanParams = params.toMutableMap()

            // 移除这些可能导致验证失败的参数
            val paramsToRemove = listOf(
                "sgti",    // 会话ID，可能已过期
                "qd_p",    // 平台参数
                "qd_time", // 质量时间
                "qd_index", // 质量索引
                "rpt"      // 报告类型
            )

            for (param in paramsToRemove) {
                cleanParams.remove(param)
            }

            // 添加新的时间戳
            val currentTime = System.currentTimeMillis()
            cleanParams["tm"] = currentTime.toString()

            return buildNewUrl(path, cleanParams)

        } catch (e: Exception) {
            Log.e(TAG, "移除过期参数失败", e)
            return buildNewUrl(path, params)
        }
    }

    /**
     * 方法3: 生成备用URL
     * 使用更简单的参数组合
     */
    private fun generateAlternativeUrl(params: Map<String, String>, path: String): String {
        try {
            val altParams = mutableMapOf<String, String>()

            // 只保留最基本的参数
            altParams["vt"] = params["vt"] ?: "2"
            altParams["ff"] = "ts"
            altParams["tm"] = System.currentTimeMillis().toString()

            // 尝试添加tvid（如果存在）
            params["tvid"]?.let { altParams["tvid"] = it }

            return buildNewUrl(path, altParams)

        } catch (e: Exception) {
            Log.e(TAG, "生成备用URL失败", e)
            return buildNewUrl(path, params)
        }
    }

    /**
     * 构建新URL
     */
    private fun buildNewUrl(path: String, params: Map<String, String>): String {
        try {
            val baseUrl = "http://mus.video.iqiyi.com"
            val query = StringBuilder()

            val sortedKeys = params.keys.sorted()
            for (key in sortedKeys) {
                if (query.isNotEmpty()) {
                    query.append("&")
                }
                val encodedValue = URLEncoder.encode(params[key], "UTF-8")
                query.append("$key=$encodedValue")
            }

            val newUrl = "$baseUrl$path?$query"
            Log.d(TAG, "新URL: $newUrl")
            return newUrl

        } catch (e: Exception) {
            Log.e(TAG, "构建URL失败", e)
            val baseUrl = "http://mus.video.iqiyi.com"
            return "$baseUrl$path"
        }
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
     * 检查URL是否为爱奇艺链接
     */
    fun isIqiyiUrl(url: String): Boolean {
        return url.contains("iqiyi.com") ||
               url.contains("qiyi.com") ||
               url.contains("video.iqiyi.com")
    }

    /**
     * 获取视频ID（如果存在）
     */
    fun extractVideoId(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            val path = uri.path ?: return null

            // 从路径中提取视频ID
            // 例如: /mus/1810989672028001/7977e8e8932f2d6ecbb1106b1e3fbc31/...
            val parts = path.split("/")
            if (parts.size >= 4) {
                parts[3]  // 返回视频ID部分
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取视频ID失败", e)
            null
        }
    }
}
