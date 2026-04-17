package com.example.floatingscreencasting.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floatingscreencasting.ui.theme.*
import com.example.floatingscreencasting.ui.AdjustmentPanelType

/**
 * 右侧状态显示区容器（简化版 - 仅显示状态概览和播放信息）
 */
@Composable
fun RightStatusPanel(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    castingStatus: String,
    isWindowVisible: Boolean,
    audioOutputMode: String,
    phoneDeviceCount: Int,
    onSeek: (Long) -> Unit,
    activeAdjustmentPanel: AdjustmentPanelType?,
    aspectRatio: AspectRatio,
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowHeight: Int,
    windowAlpha: Float,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onPositionXChange: (Int) -> Unit,
    onPositionYChange: (Int) -> Unit,
    onSizeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onCloseAdjustmentPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 状态概览卡片
        StatusOverviewCard(
            isPlaying = isPlaying,
            isWindowVisible = isWindowVisible,
            audioOutputMode = audioOutputMode,
            phoneDeviceCount = phoneDeviceCount,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. 播放信息区
        PlaybackInfoSection(
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            castingStatus = castingStatus,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. 动态调整面板
        DynamicAdjustmentPanel(
            panelType = activeAdjustmentPanel,
            aspectRatio = aspectRatio,
            windowX = windowX,
            windowY = windowY,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            windowAlpha = windowAlpha,
            onAspectRatioChange = onAspectRatioChange,
            onPositionXChange = onPositionXChange,
            onPositionYChange = onPositionYChange,
            onSizeChange = onSizeChange,
            onHeightChange = onHeightChange,
            onAlphaChange = onAlphaChange,
            onClose = onCloseAdjustmentPanel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
