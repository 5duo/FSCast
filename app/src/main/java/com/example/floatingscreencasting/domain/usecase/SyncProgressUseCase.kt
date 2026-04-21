package com.example.floatingscreencasting.domain.usecase

import com.example.floatingscreencasting.domain.model.PlaybackState
import com.example.floatingscreencasting.domain.repository.IDlnaRepository
import com.example.floatingscreencasting.utils.DlnaConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * 进度同步用例
 * 负责处理车机和手机端的进度同步
 */
class SyncProgressUseCase(
    private val dlnaRepository: IDlnaRepository
) {
    /**
     * 获取需要同步的播放状态
     * 只有差异超过阈值时才需要同步
     * @param thresholdMs 同步阈值（毫秒），默认使用DlnaConstants.SYNC_THRESHOLD_MS
     * @return Flow<PlaybackState> 需要同步的播放状态流
     */
    fun getSyncRequiredState(thresholdMs: Long = DlnaConstants.SYNC_THRESHOLD_MS): Flow<PlaybackState> {
        return dlnaRepository.getPlaybackState()
            .filter { state ->
                // 只同步正在播放的状态
                state.isPlaying
            }
            .map { state ->
                // 可以在这里添加额外的同步逻辑
                state
            }
    }

    /**
     * 检查是否需要同步进度
     * @param localPositionMs 本地播放位置（毫秒）
     * @param remotePositionMs 远程播放位置（毫秒）
     * @param thresholdMs 同步阈值（毫秒），默认使用DlnaConstants.SYNC_THRESHOLD_MS
     * @return Boolean 是否需要同步
     */
    fun needsSync(
        localPositionMs: Long,
        remotePositionMs: Long,
        thresholdMs: Long = DlnaConstants.SYNC_THRESHOLD_MS
    ): Boolean {
        val difference = kotlin.math.abs(localPositionMs - remotePositionMs)
        return difference > thresholdMs
    }

    /**
     * 获取播放状态流
     * @return Flow<PlaybackState> 播放状态流
     */
    fun getPlaybackState(): Flow<PlaybackState> {
        return dlnaRepository.getPlaybackState()
    }

    /**
     * 获取当前播放状态
     * @return PlaybackState 当前播放状态
     */
    fun getCurrentPlaybackState(): PlaybackState {
        return dlnaRepository.getCurrentPlaybackState()
    }
}
