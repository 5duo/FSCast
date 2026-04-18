package com.example.floatingscreencasting.domain.repository

import com.example.floatingscreencasting.domain.model.CastingRequest
import com.example.floatingscreencasting.domain.model.DlnaDevice
import com.example.floatingscreencasting.domain.model.PlaybackState
import kotlinx.coroutines.flow.Flow

/**
 * DLNA仓储接口
 * 定义DLNA相关操作的契约
 */
interface IDlnaRepository {

    // ==================== 投屏控制 ====================

    /**
     * 开始投屏
     * @param request 投屏请求
     * @return Result<Unit> 成功或失败
     */
    suspend fun startCasting(request: CastingRequest): Result<Unit>

    /**
     * 停止投屏
     * @return Result<Unit> 成功或失败
     */
    suspend fun stopCasting(): Result<Unit>

    // ==================== 播放控制 ====================

    /**
     * 播放
     * @return Result<Unit> 成功或失败
     */
    suspend fun play(): Result<Unit>

    /**
     * 暂停
     * @return Result<Unit> 成功或失败
     */
    suspend fun pause(): Result<Unit>

    /**
     * 跳转到指定位置
     * @param positionMs 目标位置（毫秒）
     * @return Result<Unit> 成功或失败
     */
    suspend fun seekTo(positionMs: Long): Result<Unit>

    /**
     * 设置音量
     * @param volume 音量值（0.0-1.0）
     * @return Result<Unit> 成功或失败
     */
    suspend fun setVolume(volume: Float): Result<Unit>

    /**
     * 设置静音状态
     * @param muted 是否静音
     * @return Result<Unit> 成功或失败
     */
    suspend fun setMuted(muted: Boolean): Result<Unit>

    // ==================== 设备发现 ====================

    /**
     * 发现设备
     * @return Flow<List<DlnaDevice>> 设备列表流
     */
    fun discoverDevices(): Flow<List<DlnaDevice>>

    /**
     * 获取已发现的设备列表
     * @return List<DlnaDevice> 当前设备列表
     */
    fun getDiscoveredDevices(): List<DlnaDevice>

    // ==================== 播放状态 ====================

    /**
     * 获取播放状态流
     * @return Flow<PlaybackState> 播放状态流
     */
    fun getPlaybackState(): Flow<PlaybackState>

    /**
     * 获取当前播放状态
     * @return PlaybackState 当前状态
     */
    fun getCurrentPlaybackState(): PlaybackState

    // ==================== 服务控制 ====================

    /**
     * 启动DLNA服务
     * @return Result<Unit> 成功或失败
     */
    suspend fun startService(): Result<Unit>

    /**
     * 停止DLNA服务
     * @return Result<Unit> 成功或失败
     */
    suspend fun stopService(): Result<Unit>

    /**
     * 检查服务是否正在运行
     * @return Boolean 服务运行状态
     */
    fun isServiceRunning(): Boolean

    // ==================== 投屏请求管理 ====================

    /**
     * 获取当前投屏请求
     * @return CastingRequest? 当前请求，如果没有则返回null
     */
    fun getCurrentCastingRequest(): CastingRequest?

    /**
     * 清除当前投屏请求
     */
    fun clearCastingRequest()
}
