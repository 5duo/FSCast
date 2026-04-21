package com.example.floatingscreencasting.data.repository

import com.example.floatingscreencasting.data.remote.dlna.DlnaRendererService
import com.example.floatingscreencasting.domain.model.CastingRequest
import com.example.floatingscreencasting.domain.model.PlaybackState
import com.example.floatingscreencasting.domain.model.DlnaDevice as DomainDlnaDevice
import com.example.floatingscreencasting.domain.repository.IDlnaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * DLNA仓储实现
 * 桥接Data Layer和Domain Layer
 *
 * 这个实现类将Data Layer的服务（如DlnaRendererService）
 * 适配到Domain Layer的Repository接口
 */
class DlnaRepository(
    private val rendererService: DlnaRendererService,
    private val phoneDeviceRepository: PhoneDeviceRepository
) : IDlnaRepository {

    // 当前播放状态（内部缓存）
    private var currentPlaybackState = PlaybackState.initial()

    // ==================== 投屏控制 ====================

    override suspend fun startCasting(request: CastingRequest): Result<Unit> {
        return try {
            // 设置播放回调
            rendererService.onPlayMedia = { uri, headers ->
                // TODO: 通过EventBus或回调通知Presentation播放视频
                Result.success(Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stopCasting(): Result<Unit> {
        return try {
            rendererService.onStopMedia?.invoke()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 播放控制 ====================

    override suspend fun play(): Result<Unit> {
        return try {
            rendererService.onPlay?.invoke()
            currentPlaybackState = currentPlaybackState.copy(isPlaying = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pause(): Result<Unit> {
        return try {
            rendererService.onPauseMedia?.invoke()
            currentPlaybackState = currentPlaybackState.copy(isPlaying = false)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun seekTo(positionMs: Long): Result<Unit> {
        return try {
            val target = (positionMs / 1000).toString()
            rendererService.onSeekMedia?.invoke(target)
            currentPlaybackState = currentPlaybackState.copy(currentPosition = positionMs)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setVolume(volume: Float): Result<Unit> {
        return try {
            // TODO: 实现音量设置
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setMuted(muted: Boolean): Result<Unit> {
        return try {
            // TODO: 实现静音设置
            currentPlaybackState = currentPlaybackState.copy(isMuted = muted)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 设备发现 ====================

    override fun discoverDevices(): Flow<List<DomainDlnaDevice>> {
        return phoneDeviceRepository.discoveredDevices.map { deviceList ->
            deviceList.map { device -> convertToDomainModel(device) }
        }
    }

    override fun getDiscoveredDevices(): List<DomainDlnaDevice> {
        // 从 StateFlow 获取当前值
        return phoneDeviceRepository.discoveredDevices.value.map { device -> convertToDomainModel(device) }
    }

    // ==================== 播放状态 ====================

    override fun getPlaybackState(): Flow<PlaybackState> = flow {
        emit(currentPlaybackState)
    }

    override fun getCurrentPlaybackState(): PlaybackState {
        return currentPlaybackState
    }

    // ==================== 服务控制 ====================

    override suspend fun startService(): Result<Unit> {
        return try {
            rendererService.start()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stopService(): Result<Unit> {
        return try {
            rendererService.stop()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isServiceRunning(): Boolean {
        return rendererService.isActive()
    }

    // ==================== 投屏请求管理 ====================

    override fun getCurrentCastingRequest(): CastingRequest? {
        return rendererService.getCurrentCastingRequest()?.let { request ->
            CastingRequest(
                uri = request.uri,
                title = request.metadata.ifEmpty { "未知视频" },
                headers = request.httpHeaders,
                timestamp = request.timestamp
            )
        }
    }

    override fun clearCastingRequest() {
        rendererService.clearCastingRequest()
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将Data Layer的DlnaDevice转换为Domain Layer的DlnaDevice
     */
    private fun convertToDomainModel(device: com.example.floatingscreencasting.data.remote.dlna.DlnaControlPoint.DlnaDevice): DomainDlnaDevice {
        // 从location中提取端口（格式：http://ip:port/description.xml）
        val port = try {
            val uriPattern = Regex("://(\\d+)")
            val match = uriPattern.find(device.location)?.value ?: ""
            match.toIntOrNull() ?: 0
        } catch (e: Exception) {
            49153  // DLNA默认端口
        }

        return DomainDlnaDevice(
            id = device.uuid,
            name = device.friendlyName,
            host = device.ipAddress,
            port = port,
            location = device.location,
            isActive = true
        )
    }
}
