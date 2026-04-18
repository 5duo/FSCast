package com.example.floatingscreencasting.domain.usecase

import com.example.floatingscreencasting.domain.model.CastingRequest
import com.example.floatingscreencasting.domain.repository.IDlnaRepository

/**
 * 开始投屏用例
 * 负责启动DLNA投屏
 */
class StartCastingUseCase(
    private val dlnaRepository: IDlnaRepository
) {
    /**
     * 执行开始投屏
     * @param request 投屏请求
     * @return Result<Unit> 成功或失败
     */
    suspend operator fun invoke(request: CastingRequest): Result<Unit> {
        // 验证请求
        if (!request.isValid()) {
            return Result.failure(IllegalArgumentException("Invalid casting request: URI is empty"))
        }

        return try {
            // 确保服务已启动
            if (!dlnaRepository.isServiceRunning()) {
                dlnaRepository.startService()
                    .onFailure { e ->
                        return Result.failure(Exception("Failed to start DLNA service: ${e.message}"))
                    }
            }

            // 开始投屏
            dlnaRepository.startCasting(request)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
