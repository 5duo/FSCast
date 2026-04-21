package com.example.floatingscreencasting.domain.usecase

import com.example.floatingscreencasting.domain.repository.IDlnaRepository

/**
 * 播放控制用例
 * 封装所有播放控制操作
 */
class ControlPlaybackUseCase(
    private val dlnaRepository: IDlnaRepository
) {
    /**
     * 播放
     * @return Result<Unit> 成功或失败
     */
    suspend fun play(): Result<Unit> {
        return dlnaRepository.play()
    }

    /**
     * 暂停
     * @return Result<Unit> 成功或失败
     */
    suspend fun pause(): Result<Unit> {
        return dlnaRepository.pause()
    }

    /**
     * 跳转到指定位置
     * @param positionMs 目标位置（毫秒）
     * @return Result<Unit> 成功或失败
     */
    suspend fun seekTo(positionMs: Long): Result<Unit> {
        return dlnaRepository.seekTo(positionMs)
    }

    /**
     * 设置音量
     * @param volume 音量值（0.0-1.0）
     * @return Result<Unit> 成功或失败
     */
    suspend fun setVolume(volume: Float): Result<Unit> {
        // 限制音量范围
        val clampedVolume = volume.coerceIn(0f, 1f)
        return dlnaRepository.setVolume(clampedVolume)
    }

    /**
     * 设置静音状态
     * @param muted 是否静音
     * @return Result<Unit> 成功或失败
     */
    suspend fun setMuted(muted: Boolean): Result<Unit> {
        return dlnaRepository.setMuted(muted)
    }

    /**
     * 切换播放/暂停
     * @return Result<Unit> 成功或失败
     */
    suspend fun togglePlayPause(): Result<Unit> {
        val currentState = dlnaRepository.getCurrentPlaybackState()
        return if (currentState.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    /**
     * 停止投屏
     * @return Result<Unit> 成功或失败
     */
    suspend fun stop(): Result<Unit> {
        return dlnaRepository.stopCasting()
    }
}
