package com.example.floatingscreencasting.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.floatingscreencasting.ui.composable.LeftOperationPanel
import com.example.floatingscreencasting.ui.composable.RightStatusPanel
import com.example.floatingscreencasting.ui.composable.ScreenSettingsDialog
import com.example.floatingscreencasting.ui.model.*

/**
 * 播放控制主界面
 * 组合左侧操作面板和右侧状态面板
 */
@Composable
fun PlayerControlScreen(
    uiState: MainUiState,
    showScreenSettingsDialog: Boolean,
    onToggleWindow: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onSeek: (Long) -> Unit,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onPositionXChange: (Int) -> Unit,
    onPositionYChange: (Int) -> Unit,
    onSizeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onCenterClick: () -> Unit,
    onMaximizeClick: () -> Unit,
    onDefaultClick: () -> Unit,
    onCustomClick: () -> Unit,
    onDisplayChange: (Int) -> Unit,
    onContinueWatching: () -> Unit,
    onAudioOutputChange: () -> Unit,
    onScanDevices: () -> Unit,
    onRestartWebSocket: () -> Unit,
    onOpenSettingsPanel: () -> Unit,
    onCloseSettingsPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {},  // 空的顶部栏
        modifier = modifier
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 70.dp, bottom = 16.dp)
        ) {
            // 左侧150dp留白区域（车机导航栏遮挡）
            Spacer(modifier = Modifier.width(150.dp))

            // 左侧操作区（400dp宽）
            LeftOperationPanel(
                isPlaying = uiState.isPlaying,
                isMuted = uiState.isMuted,
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                aspectRatio = uiState.aspectRatio,
                windowX = uiState.windowX,
                windowY = uiState.windowY,
                windowWidth = uiState.windowWidth,
                windowHeight = uiState.windowHeight,
                windowAlpha = uiState.windowAlpha,
                selectedDisplayId = uiState.selectedDisplayId,
                availableDisplays = uiState.availableDisplays,
                isFloatingWindowEnabled = uiState.isFloatingWindowEnabled,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onPrevious = onPrevious,
                onNext = onNext,
                onMute = onMute,
                onAudioOutputChange = onAudioOutputChange,
                onToggleWindow = onToggleWindow,
                onCenterClick = onCenterClick,
                onMaximizeClick = onMaximizeClick,
                onDefaultClick = onDefaultClick,
                onCustomClick = onCustomClick,
                onDisplayChange = onDisplayChange,
                onOpenSettingsPanel = onOpenSettingsPanel
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧状态显示区（仅显示状态概览和播放信息）
            RightStatusPanel(
                isPlaying = uiState.isPlaying,
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                castingStatus = uiState.castingStatus,
                isWindowVisible = uiState.isWindowVisible,
                audioOutputMode = uiState.audioOutputMode,
                phoneDeviceCount = uiState.phoneDeviceCount,
                videoTitle = uiState.currentVideoTitle,
                videoUrl = uiState.currentVideoUrl,
                isWebSocketServerRunning = uiState.isWebSocketServerRunning,
                onSeek = onSeek,
                onRestartWebSocket = onRestartWebSocket,
                onScanDevices = onScanDevices,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 屏幕设置弹窗
    ScreenSettingsDialog(
        isVisible = showScreenSettingsDialog,
        aspectRatio = uiState.aspectRatio,
        windowX = uiState.windowX,
        windowY = uiState.windowY,
        windowWidth = uiState.windowWidth,
        windowHeight = uiState.windowHeight,
        windowAlpha = uiState.windowAlpha,
        onAspectRatioChange = onAspectRatioChange,
        onPositionXChange = onPositionXChange,
        onPositionYChange = onPositionYChange,
        onSizeChange = onSizeChange,
        onHeightChange = onHeightChange,
        onAlphaChange = onAlphaChange,
        onDismiss = onCloseSettingsPanel
    )
}
