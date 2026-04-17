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

/**
 * 右侧状态显示区容器
 * 使用固定高度确保播放信息卡片底部与左侧悬浮窗控制卡片底部对齐
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
    videoTitle: String,
    videoUrl: String,
    isWebSocketServerRunning: Boolean,  // 新增参数
    onSeek: (Long) -> Unit,
    onRestartWebSocket: () -> Unit,
    onScanDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 使用简单Column结构，固定高度控制
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 状态概览卡片
        StatusOverviewCard(
            isPlaying = isPlaying,
            isWindowVisible = isWindowVisible,
            audioOutputMode = audioOutputMode,
            phoneDeviceCount = phoneDeviceCount,
            isWebSocketServerRunning = isWebSocketServerRunning,  // 新增参数
            onRestartWebSocket = onRestartWebSocket,
            onScanDevices = onScanDevices,
            modifier = Modifier.fillMaxWidth()
        )

        // 2. 播放信息卡片 - 固定高度283dp
        PlaybackInfoSection(
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            castingStatus = castingStatus,
            videoTitle = videoTitle,
            videoUrl = videoUrl,
            onSeek = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .height(283.dp)
        )
    }
}
