package com.example.floatingscreencasting.cache

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File

/**
 * 视频缓存管理器
 * 使用ExoPlayer的Cache功能缓存视频，支持离线播放
 */
@UnstableApi
class VideoCacheManager private constructor(context: Context) {

    companion object {
        private const val TAG = "VideoCacheManager"
        private const val CACHE_SIZE_BYTES = 500 * 1024 * 1024L // 500MB缓存空间
        private const val CACHE_FOLDER_NAME = "video_cache"

        @Volatile
        private var instance: VideoCacheManager? = null

        fun getInstance(context: Context): VideoCacheManager {
            return instance ?: synchronized(this) {
                instance ?: VideoCacheManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val cache: Cache

    init {
        val cacheDir = File(context.cacheDir, CACHE_FOLDER_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // 创建LRU缓存，最多使用500MB空间
        val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES)
        val databaseProvider = StandaloneDatabaseProvider(context)
        cache = SimpleCache(cacheDir, evictor, databaseProvider)

        android.util.Log.d(TAG, "视频缓存初始化完成，目录: ${cacheDir.absolutePath}, 大小: ${CACHE_SIZE_BYTES / 1024 / 1024}MB")
    }

    /**
     * 创建支持缓存的DataSource.Factory
     */
    fun createCachedDataSourceFactory(
        context: Context,
        headers: Map<String, String> = emptyMap()
    ): DataSource.Factory {
        // 创建HTTP数据源
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MiTV/1.0 (Linux;Android 12) MI_TV_4")
            .setKeepPostFor302Redirects(true)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(30 * 1000)
            .setReadTimeoutMs(30 * 1000)

        // 创建默认数据源工厂
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // 创建缓存数据源工厂
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) // 网络错误时忽略缓存
    }

    /**
     * 获取缓存大小（字节）
     */
    fun getCacheSize(): Long {
        return cache.cacheSpace
    }

    /**
     * 清空所有缓存
     */
    fun clearCache() {
        val keys = cache.keys
        for (key in keys) {
            cache.removeResource(key)
        }
        android.util.Log.d(TAG, "缓存已清空，删除了 ${keys.size} 个资源")
    }

    /**
     * 获取缓存中的所有URL
     */
    fun getCacheKeys(): Set<String> {
        return cache.keys
    }

    /**
     * 检查某个URL是否已缓存
     */
    fun isCached(url: String): Boolean {
        return cache.getCachedSpans(CacheUtil.buildCacheKey(url)).isNotEmpty()
    }

    /**
     * 获取某个URL的缓存进度（0.0-1.0）
     */
    fun getCacheProgress(url: String): Float {
        val spans = cache.getCachedSpans(CacheUtil.buildCacheKey(url))
        if (spans.isEmpty()) return 0f

        var totalCached = 0L
        for (span in spans) {
            totalCached += (span.length)
        }

        // 注意：这里无法获取总大小，返回1.0表示有缓存
        return if (totalCached > 0) 1f else 0f
    }

    /**
     * 释放缓存资源
     */
    fun release() {
        try {
            cache.release()
            android.util.Log.d(TAG, "缓存资源已释放")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "释放缓存资源失败", e)
        }
    }

    /**
     * 缓存工具类
     */
    private object CacheUtil {
        fun buildCacheKey(url: String): String {
            return url
        }
    }
}
