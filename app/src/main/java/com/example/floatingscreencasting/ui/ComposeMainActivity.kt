package com.example.floatingscreencasting.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.floatingscreencasting.dlna.DlnaDmrService
import com.example.floatingscreencasting.events.MuteEvent
import com.example.floatingscreencasting.presentation.VideoPresentation
import com.example.floatingscreencasting.ui.composable.*
import com.example.floatingscreencasting.ui.theme.FloatingScreenCastingTheme
import com.example.floatingscreencasting.ui.composable.DisplayInfo
import com.example.floatingscreencasting.history.PlaybackHistoryManager
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Compose版本的主Activity
 * 使用Jetpack Compose Material 3构建UI
 */
class ComposeMainActivity : AppCompatActivity() {

    private lateinit var displayManager: DisplayManager
    private var videoPresentation: VideoPresentation? = null
    private lateinit var preferencesManager: PreferencesManager

    // 播放历史管理器（延迟初始化）
    private val historyManager: PlaybackHistoryManager by lazy {
        PlaybackHistoryManager.getInstance(this)
    }

    // DLNA服务
    private lateinit var dlnaService: DlnaDmrService

    // 驾驶屏固定Display ID 2
    private val drivingDisplayId = 2

    // 进度更新：使用协程替代Handler
    private var progressUpdateJob: Job? = null
    private val progressUpdateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 自定义配置存储
    private data class CustomConfig(
        val x: Int = 429,
        val y: Int = 1,
        val width: Int = 1063,
        val height: Int = 652,
        val alpha: Float = 0.41f
    )

    private var customConfig = CustomConfig()

    // 播放错误广播接收器
    private val playbackErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val error = intent?.getStringExtra("error")
            val errorCode = intent?.getIntExtra("errorCode", 0) ?: 0

            Log.e("ComposeMainActivity", "收到播放错误: $error (代码: $errorCode)")

            // 更新UI状态
            _uiState.value = uiState.value.copy(
                castingStatus = "播放失败",
                isPlaying = false
            )

            // 显示错误提示
            when {
                error?.contains("959") == true -> {
                    Toast.makeText(
                        this@ComposeMainActivity,
                        "Bilibili视频播放失败（959错误）\n建议: 使用其他平台投屏",
                        Toast.LENGTH_LONG
                    ).show()
                }
                error?.contains("403") == true -> {
                    Toast.makeText(
                        this@ComposeMainActivity,
                        "视频访问被拒绝(403)\n建议: 重新投屏",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    val errorMsg = if (errorCode == 2004) {
                        "视频播放失败(HTTP $errorCode)\n可能是平台限制"
                    } else {
                        "视频播放失败: $error"
                    }
                    Toast.makeText(
                        this@ComposeMainActivity,
                        errorMsg,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // UI状态
    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    // DisplayListener
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            val display = displayManager.getDisplay(displayId)
            display?.let {
                if (isDrivingDisplay(it) || isPresentationDisplay(it)) {
                    showPresentationOnDisplay(it)
                }
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (videoPresentation?.display?.displayId == displayId) {
                videoPresentation?.dismiss()
                videoPresentation = null
                _uiState.value = uiState.value.copy(
                    isWindowVisible = false,
                    castingStatus = "等待投屏",
                    isPlaying = false
                )
            }
        }

        override fun onDisplayChanged(displayId: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置透明状态栏
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        preferencesManager = PreferencesManager(this)

        initializeDisplays()
        loadSettings()
        initializeDlnaService()
        updateContinueWatchingStatus()

        // 注册广播接收器
        registerReceiver(playbackErrorReceiver, IntentFilter("com.example.floatingscreencasting.PLAYBACK_ERROR"))
        EventBus.getDefault().register(this)

        // 启动进度更新（使用协程）
        startProgressUpdate()

        // 设置Compose UI
        setContent {
            FloatingScreenCastingTheme(
                darkTheme = true,      // 使用深色主题
                modernDesign = true     // 启用现代设计
            ) {
                MainScreen(
                    uiState = uiState.value,
                    onToggleWindow = { toggleWindow() },
                    onPlayPause = { togglePlayPause() },
                    onStop = { stop() },
                    onPrevious = { previous() },
                    onNext = { next() },
                    onMute = { toggleMute() },
                    onSeek = { positionMs -> seekTo(positionMs) },
                    onAspectRatioChange = { ratio -> changeAspectRatio(ratio) },
                    onPositionXChange = { x -> changePositionX(x) },
                    onPositionYChange = { y -> changePositionY(y) },
                    onSizeChange = { width -> changeSize(width) },
                    onHeightChange = { height -> changeHeight(height) },
                    onAlphaChange = { alpha -> changeAlpha(alpha) },
                    onCenterClick = { centerWindow() },
                    onMaximizeClick = { maximizeWindow() },
                    onDefaultClick = { restoreDefault() },
                    onCustomClick = { saveCustomConfig() },
                    onDisplayChange = { displayId -> changeDisplay(displayId) },
                    onContinueWatching = { continueWatching() }
                )
            }
        }
    }

    @Composable
    fun MainScreen(
        uiState: MainUiState,
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
        onContinueWatching: () -> Unit
    ) {
        Scaffold(
            topBar = {}  // 空的顶部栏
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(start = 150.dp, end = 20.dp, top = 70.dp, bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 继续观看卡片（如果有可继续观看的内容）
                if (uiState.hasContinueWatching) {
                    ContinueWatchingCard(
                        hasContinueWatching = uiState.hasContinueWatching,
                        title = uiState.lastPlayedTitle,
                        progress = uiState.lastPlayedProgress,
                        onContinueWatching = onContinueWatching,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 悬浮窗控制卡片
                ModernCastingControlCard(
                    isWindowVisible = uiState.isWindowVisible,
                    castingStatus = uiState.castingStatus,
                    selectedDisplayId = uiState.selectedDisplayId,
                    availableDisplays = uiState.availableDisplays,
                    onToggleWindow = onToggleWindow,
                    onDisplayChange = onDisplayChange,
                    modifier = Modifier.fillMaxWidth()
                )

                // 播放控制卡片
                ModernPlaybackControlCard(
                    isPlaying = uiState.isPlaying,
                    currentPosition = uiState.currentPosition,
                    duration = uiState.duration,
                    isMuted = uiState.isMuted,
                    onPlayPause = onPlayPause,
                    onStop = onStop,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onMute = onMute,
                    onSeek = { onSeek(it.toLong()) },
                    modifier = Modifier.fillMaxWidth()
                )

                // 设置卡片
                ModernSettingsCard(
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
                    onCenterClick = onCenterClick,
                    onMaximizeClick = onMaximizeClick,
                    onDefaultClick = onDefaultClick,
                    onCustomClick = onCustomClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 底部信息
                Text(
                    text = "🎬 DLNA投屏接收器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // ==================== 控制方法 ====================

    private fun toggleWindow() {
        lifecycleScope.launch {
            if (videoPresentation == null) {
                // 使用用户选择的Display ID
                val selectedDisplayId = uiState.value.selectedDisplayId
                val display = displayManager.getDisplay(selectedDisplayId)
                if (display != null) {
                    showPresentationOnDisplay(display)
                } else {
                    // 如果选择的Display不可用，查找其他可用Display
                    val presentationDisplay = displayManager.displays.firstOrNull {
                        isPresentationDisplay(it)
                    }
                    if (presentationDisplay != null) {
                        showPresentationOnDisplay(presentationDisplay)
                    }
                }
            } else {
                videoPresentation?.dismiss()
                videoPresentation = null
                _uiState.value = uiState.value.copy(isWindowVisible = false)
            }
        }
    }

    private fun togglePlayPause() {
        val wasPlaying = uiState.value.isPlaying
        Log.d("ComposeMainActivity", "切换播放状态: 当前=$wasPlaying")

        // 立即更新UI状态（乐观更新）
        _uiState.value = uiState.value.copy(isPlaying = !wasPlaying)

        // 执行播放器操作（ExoPlayer方法是线程安全的）
        try {
            if (wasPlaying) {
                videoPresentation?.pause()
            } else {
                videoPresentation?.play()
            }
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "播放控制失败", e)
            // 恢复UI状态
            _uiState.value = uiState.value.copy(isPlaying = wasPlaying)
        }
    }

    private fun stop() {
        _uiState.value = uiState.value.copy(
            isPlaying = false,
            currentPosition = 0,
            duration = 0
        )
        try {
            videoPresentation?.stop()
            videoPresentation?.resetToInitialState()
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "停止播放失败", e)
        }
    }

    private fun previous() {
        try {
            videoPresentation?.previous()
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "上一个失败", e)
        }
    }

    private fun next() {
        try {
            videoPresentation?.next()
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "下一个失败", e)
        }
    }

    private fun toggleMute() {
        val newMutedState = !uiState.value.isMuted
        _uiState.value = uiState.value.copy(isMuted = newMutedState)
        videoPresentation?.setMuted(newMutedState)
        EventBus.getDefault().post(MuteEvent(newMutedState))
    }

    private fun seekTo(positionSeconds: Long) {
        try {
            // 转换为毫秒传给 ExoPlayer
            val positionMs = positionSeconds * 1000
            Log.d("ComposeMainActivity", "Seek到位置: ${positionSeconds}s = ${positionMs}ms")
            videoPresentation?.seekTo(positionMs)

            // 立即更新UI进度显示
            val player = videoPresentation?.getExoPlayer()
            if (player != null) {
                val durationSeconds = player.duration / 1000

                _uiState.value = _uiState.value.copy(
                    currentPosition = positionSeconds,
                    duration = durationSeconds
                )

                Log.d("ComposeMainActivity", "Seek后更新UI: ${positionSeconds}s / ${durationSeconds}s")
            }
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "Seek失败", e)
        }
    }

    private fun changeAspectRatio(ratio: AspectRatio) {
        _uiState.value = uiState.value.copy(aspectRatio = ratio)
        // 只在非自定义比例时自动计算高度
        if (ratio != AspectRatio.CUSTOM) {
            val height = preferencesManager.calculateHeightForWidth(uiState.value.windowWidth, ratio.displayName)
            _uiState.value = uiState.value.copy(windowHeight = height)
            videoPresentation?.apply {
                windowWidth = uiState.value.windowWidth
                windowHeight = height
            }
        }
        saveSettings()
    }

    private fun changeHeight(height: Int) {
        _uiState.value = uiState.value.copy(windowHeight = height)
        videoPresentation?.windowHeight = height
        // 自动更新自定义配置
        customConfig = customConfig.copy(height = height)
        saveSettings()
    }

    private fun changePositionX(x: Int) {
        _uiState.value = uiState.value.copy(windowX = x)
        videoPresentation?.windowX = x
        // 自动更新自定义配置
        customConfig = customConfig.copy(x = x)
        saveSettings()
    }

    private fun changePositionY(y: Int) {
        _uiState.value = uiState.value.copy(windowY = y)
        videoPresentation?.windowY = y
        // 自动更新自定义配置
        customConfig = customConfig.copy(y = y)
        saveSettings()
    }

    private fun changeSize(width: Int) {
        val height = preferencesManager.calculateHeightForWidth(width, uiState.value.aspectRatio.displayName)
        _uiState.value = uiState.value.copy(
            windowWidth = width,
            windowHeight = height
        )
        videoPresentation?.apply {
            windowWidth = width
            windowHeight = height
        }
        // 自动更新自定义配置
        customConfig = customConfig.copy(width = width, height = height)
        saveSettings()
    }

    private fun changeAlpha(alpha: Float) {
        _uiState.value = uiState.value.copy(windowAlpha = alpha)
        videoPresentation?.windowAlpha = alpha
        // 自动更新自定义配置
        customConfig = customConfig.copy(alpha = alpha)
        saveSettings()
    }

    private fun centerWindow() {
        val displayWidth = 1920
        val centeredX = (displayWidth - uiState.value.windowWidth) / 2
        _uiState.value = uiState.value.copy(windowX = centeredX)
        videoPresentation?.windowX = centeredX
        // 自动更新自定义配置
        customConfig = customConfig.copy(x = centeredX)
        saveSettings()
    }

    private fun maximizeWindow() {
        // 全屏参数
        val maxX = 429
        val maxY = 1
        val maxWidth = 1063
        val maxHeight = 652

        _uiState.value = uiState.value.copy(
            windowWidth = maxWidth,
            windowHeight = maxHeight,
            windowX = maxX,
            windowY = maxY,
            aspectRatio = AspectRatio.CUSTOM
        )
        videoPresentation?.apply {
            windowWidth = maxWidth
            windowHeight = maxHeight
            windowX = maxX
            windowY = maxY
        }
        saveSettings()
    }

    private fun saveCustomConfig() {
        // 保存当前状态到自定义配置
        customConfig = CustomConfig(
            x = uiState.value.windowX,
            y = uiState.value.windowY,
            width = uiState.value.windowWidth,
            height = uiState.value.windowHeight,
            alpha = uiState.value.windowAlpha
        )
        Toast.makeText(this, "已保存当前设置到自定义", Toast.LENGTH_SHORT).show()
    }

    private fun restoreDefault() {
        // 恢复默认值
        val defaultX = 428
        val defaultY = 332
        val defaultWidth = 434
        val defaultAlpha = 0.41f
        val defaultHeight = preferencesManager.calculateHeightForWidth(defaultWidth, "16:9")

        _uiState.value = uiState.value.copy(
            windowX = defaultX,
            windowY = defaultY,
            windowWidth = defaultWidth,
            windowHeight = defaultHeight,
            windowAlpha = defaultAlpha,
            aspectRatio = AspectRatio.RATIO_16_9
        )
        videoPresentation?.apply {
            windowX = defaultX
            windowY = defaultY
            windowWidth = defaultWidth
            windowHeight = defaultHeight
            windowAlpha = defaultAlpha
        }
        saveSettings()
    }

    private fun changeDisplay(displayId: Int) {
        _uiState.value = uiState.value.copy(selectedDisplayId = displayId)
        // 如果当前有Presentation，需要重新创建到新屏幕
        if (videoPresentation != null) {
            videoPresentation?.dismiss()
            videoPresentation = null
            _uiState.value = uiState.value.copy(isWindowVisible = false)

            // 在新屏幕上创建Presentation
            lifecycleScope.launch {
                val display = displayManager.getDisplay(displayId)
                if (display != null) {
                    showPresentationOnDisplay(display)
                }
            }
        }
        saveSettings()
    }

    /**
     * 更新继续观看状态
     */
    private fun updateContinueWatchingStatus() {
        val lastPlayed = historyManager.getLastPlayed()
        if (lastPlayed != null && historyManager.hasContinueWatching()) {
            _uiState.value = uiState.value.copy(
                hasContinueWatching = true,
                lastPlayedTitle = lastPlayed.title,
                lastPlayedProgress = lastPlayed.getProgressPercent()
            )
            Log.d("ComposeMainActivity", "继续观看可用: ${lastPlayed.title}, 进度: ${lastPlayed.getProgressPercent()}%")
        } else {
            _uiState.value = uiState.value.copy(
                hasContinueWatching = false,
                lastPlayedTitle = "",
                lastPlayedProgress = 0
            )
        }
    }

    /**
     * 继续观看上次的视频
     */
    private fun continueWatching() {
        val lastPlayed = historyManager.getLastPlayed()
        if (lastPlayed != null) {
            lifecycleScope.launch {
                // 确保浮窗已显示
                if (videoPresentation == null) {
                    val selectedDisplayId = uiState.value.selectedDisplayId
                    val display = displayManager.getDisplay(selectedDisplayId)
                    if (display != null) {
                        showPresentationOnDisplay(display)
                        delay(500) // 等待Presentation初始化
                    }
                }

                // 播放视频
                videoPresentation?.playMedia(lastPlayed.uri)

                // 跳转到上次播放的位置
                if (lastPlayed.positionMs > 0) {
                    videoPresentation?.seekTo(lastPlayed.positionMs)
                }

                Log.d("ComposeMainActivity", "继续观看: ${lastPlayed.title}, 从 ${lastPlayed.positionMs / 1000}s 开始")
            }
        } else {
            Toast.makeText(this, "没有可继续观看的视频", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 初始化和辅助方法 ====================
    private fun initializeDisplays() {
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        val allDisplays = displayManager.displays
        Log.d("ComposeMainActivity", "系统共有 ${allDisplays.size} 个显示设备")

        // 更新可用屏幕列表到UI状态
        val displayInfoList = allDisplays.map { display ->
            com.example.floatingscreencasting.ui.composable.DisplayInfo(id = display.displayId, name = display.name)
        }
        _uiState.value = uiState.value.copy(availableDisplays = displayInfoList)

        allDisplays.forEach { display ->
            Log.d("ComposeMainActivity", "  Display ID: ${display.displayId}, Name: ${display.name}")
        }

        // 自动在用户选择的屏幕上打开浮窗
        lifecycleScope.launch {
            delay(500) // 等待UI初始化完成
            val selectedDisplayId = uiState.value.selectedDisplayId
            val targetDisplay = displayManager.getDisplay(selectedDisplayId)

            if (targetDisplay != null) {
                Log.d("ComposeMainActivity", "应用启动：自动在Display $selectedDisplayId 上打开浮窗")
                showPresentationOnDisplay(targetDisplay)
            } else {
                Log.w("ComposeMainActivity", "选择的Display $selectedDisplayId 不可用，尝试使用其他可用Display")
                // 如果选择的Display不可用，查找其他可用Display
                val presentationDisplay = allDisplays.firstOrNull {
                    isPresentationDisplay(it)
                }
                if (presentationDisplay != null) {
                    showPresentationOnDisplay(presentationDisplay)
                }
            }
        }
    }

    private fun showPresentationOnDisplay(display: Display) {
        try {
            Log.d("ComposeMainActivity", "开始创建VideoPresentation...")
            VideoPresentation(this, display).apply {
                videoPresentation = this
                show()
                Log.d("ComposeMainActivity", "VideoPresentation.show()调用成功")

                // 应用当前设置
                windowX = uiState.value.windowX
                windowY = uiState.value.windowY
                windowWidth = uiState.value.windowWidth
                windowHeight = uiState.value.windowHeight
                windowAlpha = uiState.value.windowAlpha
                setMuted(uiState.value.isMuted)
                Log.d("ComposeMainActivity", "Presentation参数设置完成")

                _uiState.value = uiState.value.copy(isWindowVisible = true)
                Log.d("ComposeMainActivity", "Presentation创建成功，isWindowVisible = true")
            }
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "创建Presentation失败", e)
        }
    }

    private fun initializeDlnaService() {
        dlnaService = DlnaDmrService.getInstance(this)

        // 立即设置回调（不等待协程）
        dlnaService.apply {
            Log.d("ComposeMainActivity", "设置DLNA服务回调")

            onCastingStateChanged = { isCasting, title ->
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.d("ComposeMainActivity", "投屏状态变化: isCasting=$isCasting, title=$title")
                    _uiState.value = uiState.value.copy(
                        castingStatus = if (isCasting) title ?: "投屏中" else "等待投屏",
                        isPlaying = isCasting
                    )
                }
            }

            onErrorOccurred = { error ->
                lifecycleScope.launch(Dispatchers.Main) {
                    Log.e("ComposeMainActivity", "DLNA错误: $error")
                    Toast.makeText(this@ComposeMainActivity, error, Toast.LENGTH_SHORT).show()
                }
            }

            onPlayMedia = { uri ->
                Log.d("ComposeMainActivity", "收到onPlayMedia回调: $uri")
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        Log.d("ComposeMainActivity", "videoPresentation是否为null: ${videoPresentation == null}")
                        videoPresentation?.playMedia(uri)
                        if (uri.isNotEmpty()) {
                            dlnaService.updateTransportState("PLAYING")
                            // 播放开始
                            _uiState.value = uiState.value.copy(isPlaying = true)
                        }
                    } catch (e: Exception) {
                        Log.e("ComposeMainActivity", "播放视频失败", e)
                    }
                }
            }

            onStopMedia = {
                Log.d("ComposeMainActivity", "收到停止回调")
                try {
                    videoPresentation?.stop()
                    // 停止播放，但保持悬浮窗显示，恢复到初始状态
                    videoPresentation?.resetToInitialState()
                    _uiState.value = uiState.value.copy(
                        isPlaying = false,
                        castingStatus = "等待投屏",
                        currentPosition = 0,
                        duration = 0
                    )
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "停止播放失败", e)
                }
            }

            onPauseMedia = {
                Log.d("ComposeMainActivity", "收到暂停回调")
                try {
                    videoPresentation?.pause()
                    // 暂停播放
                    _uiState.value = uiState.value.copy(isPlaying = false)
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "暂停播放失败", e)
                }
            }

            onPlay = {
                Log.d("ComposeMainActivity", "收到恢复播放回调")
                try {
                    videoPresentation?.play()
                    // 恢复播放
                    _uiState.value = uiState.value.copy(isPlaying = true)
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "恢复播放失败", e)
                }
            }

            onSeekMedia = { target ->
                Log.d("ComposeMainActivity", "收到Seek回调: target='$target'")
                try {
                    val seconds: Long = when {
                        // HH:MM:SS格式
                        target.contains(":") && target.split(":").size == 3 -> {
                            val parts = target.split(":")
                            (parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()).toLong()
                        }
                        // MM:SS格式或只有一个冒号
                        target.contains(":") && target.split(":").size == 2 -> {
                            val parts = target.split(":")
                            (parts[0].toInt() * 60 + parts[1].toInt()).toLong()
                        }
                        // 纯数字格式
                        target.all { it.isDigit() } -> {
                            target.toLong()
                        }
                        // 浮点数格式或其他
                        else -> {
                            target.toFloat().toInt().toLong()
                        }
                    }

                    Log.d("ComposeMainActivity", "解析后的秒数: ${seconds}s")
                    videoPresentation?.seekTo(seconds * 1000L)
                    Log.d("ComposeMainActivity", "Seek完成: ${seconds * 1000L}ms")
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "Seek失败: target='$target'", e)
                }
            }
        }

        // 启动DLNA服务
        lifecycleScope.launch {
            dlnaService.start()
            Log.d("ComposeMainActivity", "DLNA服务启动完成")

            // 延迟一点设置回调，确保服务完全启动
            delay(100)

            // 设置播放状态回调
            Log.d("ComposeMainActivity", "准备设置播放状态回调")
            dlnaService.setPlaybackCallbacks(
                onDuration = {
                    try {
                        val durationMs = videoPresentation?.getExoPlayer()?.duration ?: -1L
                        val duration = if (durationMs > 0) durationMs / 1000L else 0L
                        Log.d("ComposeMainActivity", "获取视频时长: ${duration}s (原始: ${durationMs}ms)")
                        duration
                    } catch (e: Exception) {
                        Log.e("ComposeMainActivity", "获取时长失败", e)
                        0L
                    }
                },
                onPosition = {
                    try {
                        val positionMs = videoPresentation?.getExoPlayer()?.currentPosition ?: 0L
                        val position = if (positionMs >= 0) positionMs / 1000L else 0L
                        Log.d("ComposeMainActivity", "获取播放位置: ${position}s (原始: ${positionMs}ms)")
                        position
                    } catch (e: Exception) {
                        Log.e("ComposeMainActivity", "获取位置失败", e)
                        0L
                    }
                }
            )
            Log.d("ComposeMainActivity", "播放状态回调设置完成")
        }
    }

    private fun loadSettings() {
        val savedSettings = preferencesManager.windowPosition
        val aspectRatio = when (savedSettings.aspectRatio) {
            "4:3" -> AspectRatio.RATIO_4_3
            "竖屏" -> AspectRatio.RATIO_PORTRAIT
            "自定义" -> AspectRatio.CUSTOM
            else -> AspectRatio.RATIO_16_9
        }

        _uiState.value = uiState.value.copy(
            aspectRatio = aspectRatio,
            windowX = savedSettings.x,
            windowY = savedSettings.y,
            windowWidth = savedSettings.width,
            windowHeight = savedSettings.height,
            windowAlpha = savedSettings.alpha / 100f,
            selectedDisplayId = savedSettings.displayId
        )

        videoPresentation?.apply {
            windowX = savedSettings.x
            windowY = savedSettings.y
            windowWidth = savedSettings.width
            windowHeight = savedSettings.height
            windowAlpha = savedSettings.alpha / 100f
        }
    }

    private fun saveSettings() {
        val settings = PreferencesManager.WindowPosition(
            x = uiState.value.windowX,
            y = uiState.value.windowY,
            width = uiState.value.windowWidth,
            height = uiState.value.windowHeight,
            alpha = (uiState.value.windowAlpha * 100).toInt(),
            aspectRatio = uiState.value.aspectRatio.displayName,
            displayId = uiState.value.selectedDisplayId
        )
        preferencesManager.windowPosition = settings
    }

    /**
     * 启动进度更新协程
     */
    private fun startProgressUpdate() {
        // 取消之前的任务
        progressUpdateJob?.cancel()

        progressUpdateJob = progressUpdateScope.launch {
            while (isActive) {
                try {
                    val player = videoPresentation?.getExoPlayer()
                    if (player != null) {
                        val currentPosition = player.currentPosition / 1000
                        val duration = player.duration / 1000
                        val isPlaying = player.isPlaying

                        // 只在值发生实际变化时更新UI
                        if (_uiState.value.currentPosition != currentPosition ||
                            _uiState.value.duration != duration ||
                            _uiState.value.isPlaying != isPlaying) {

                            _uiState.value = _uiState.value.copy(
                                isPlaying = isPlaying,
                                currentPosition = currentPosition,
                                duration = duration
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "进度更新失败", e)
                }

                // 每秒更新一次
                delay(1000)
            }
        }
    }

    /**
     * 停止进度更新
     */
    private fun stopProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun updateProgressState() {
        // 保留此方法以兼容现有代码，但不再使用
    }

    // ==================== EventBus ====================

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMuteEvent(event: MuteEvent) {
        _uiState.value = uiState.value.copy(isMuted = event.isMuted)
    }

    // ==================== 生命周期 ====================

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        displayManager.unregisterDisplayListener(displayListener)
        unregisterReceiver(playbackErrorReceiver)
        EventBus.getDefault().unregister(this)
        videoPresentation?.dismiss()
    }

    // ==================== 辅助方法 ====================

    private fun isDrivingDisplay(display: Display): Boolean {
        return display.displayId == drivingDisplayId
    }

    private fun isPresentationDisplay(display: Display): Boolean {
        return display.flags and Display.FLAG_PRESENTATION != 0
    }

    private fun addressToString(address: ByteArray): String {
        return address.joinToString(":") { String.format("%02X", it) }
    }
}

/**
 * 主界面UI状态
 */
data class MainUiState(
    val isWindowVisible: Boolean = false,
    val castingStatus: String = "等待投屏",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isMuted: Boolean = true,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val windowX: Int = 428,
    val windowY: Int = 332,
    val windowWidth: Int = 434,
    val windowHeight: Int = 244,
    val windowAlpha: Float = 0.41f,
    val currentAudioOutput: String = "系统扬声器",
    val selectedDisplayId: Int = 2,
    val availableDisplays: List<DisplayInfo> = emptyList(),
    val hasContinueWatching: Boolean = false,
    val lastPlayedTitle: String = "",
    val lastPlayedProgress: Int = 0
)

// DisplayInfo已定义在com.example.floatingscreencasting.ui.composable包中
