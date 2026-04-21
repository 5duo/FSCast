package com.example.floatingscreencasting.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.floatingscreencasting.domain.model.PlaybackState
import com.example.floatingscreencasting.domain.usecase.ControlPlaybackUseCase
import com.example.floatingscreencasting.domain.usecase.StartCastingUseCase
import com.example.floatingscreencasting.domain.usecase.SyncProgressUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面ViewModel
 * 管理UI状态和业务逻辑
 */
class MainViewModel(
    private val controlPlayback: ControlPlaybackUseCase,
    private val syncProgress: SyncProgressUseCase
) : ViewModel() {

    // ==================== 播放状态 ====================

    private val _playbackState = MutableStateFlow(PlaybackState.initial())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // ==================== UI状态 ====================

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _castingStatus = MutableStateFlow("等待投屏")
    val castingStatus: StateFlow<String> = _castingStatus.asStateFlow()

    // ==================== 窗口状态 ====================

    private val _windowVisible = MutableStateFlow(false)
    val windowVisible: StateFlow<Boolean> = _windowVisible.asStateFlow()

    private val _selectedDisplayId = MutableStateFlow(2)
    val selectedDisplayId: StateFlow<Int> = _selectedDisplayId.asStateFlow()

    // ==================== 初始化 ====================

    init {
        observePlaybackState()
    }

    /**
     * 观察播放状态
     */
    private fun observePlaybackState() {
        viewModelScope.launch {
            syncProgress.getPlaybackState().collect { state ->
                _playbackState.value = state
            }
        }
    }

    // ==================== 播放控制 ====================

    /**
     * 播放
     */
    fun play() {
        viewModelScope.launch {
            _isLoading.value = true
            controlPlayback.play()
                .onFailure { e ->
                    _errorMessage.value = "播放失败: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    /**
     * 暂停
     */
    fun pause() {
        viewModelScope.launch {
            controlPlayback.pause()
                .onFailure { e ->
                    _errorMessage.value = "暂停失败: ${e.message}"
                }
        }
    }

    /**
     * 切换播放/暂停
     */
    fun togglePlayPause() {
        viewModelScope.launch {
            _isLoading.value = true
            controlPlayback.togglePlayPause()
                .onFailure { e ->
                    _errorMessage.value = "操作失败: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            controlPlayback.seekTo(positionMs)
                .onFailure { e ->
                    _errorMessage.value = "跳转失败: ${e.message}"
                }
        }
    }

    /**
     * 设置音量
     */
    fun setVolume(volume: Float) {
        viewModelScope.launch {
            controlPlayback.setVolume(volume)
                .onFailure { e ->
                    _errorMessage.value = "设置音量失败: ${e.message}"
                }
        }
    }

    /**
     * 切换静音状态
     */
    fun toggleMute() {
        viewModelScope.launch {
            val newState = !playbackState.value.isMuted
            controlPlayback.setMuted(newState)
                .onFailure { e ->
                    _errorMessage.value = "设置静音失败: ${e.message}"
                }
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        viewModelScope.launch {
            controlPlayback.stop()
                .onFailure { e ->
                    _errorMessage.value = "停止失败: ${e.message}"
                }
        }
    }

    // ==================== 窗口控制 ====================

    /**
     * 切换悬浮窗显示状态
     */
    fun toggleWindowVisible() {
        _windowVisible.value = !_windowVisible.value
    }

    /**
     * 设置选中的显示ID
     */
    fun setSelectedDisplayId(displayId: Int) {
        _selectedDisplayId.value = displayId
    }

    // ==================== 错误处理 ====================

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 更新投屏状态
     */
    fun updateCastingStatus(status: String) {
        _castingStatus.value = status
    }

    // ==================== 状态获取 ====================

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = playbackState.value.isPlaying

    /**
     * 检查是否正在加载
     */
    fun isLoading(): Boolean = _isLoading.value

    /**
     * 检查窗口是否可见
     */
    fun isWindowVisible(): Boolean = _windowVisible.value
}
