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
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.floatingscreencasting.dlna.AudioOutputController
import com.example.floatingscreencasting.dlna.PhoneDeviceManager
import com.example.floatingscreencasting.data.remote.dlna.DlnaControlPoint
import com.example.floatingscreencasting.data.remote.dlna.DlnaRendererService
import com.example.floatingscreencasting.events.MuteEvent
import com.example.floatingscreencasting.events.PlaybackEndEvent
import com.example.floatingscreencasting.presentation.VideoPresentation
import com.example.floatingscreencasting.presentation.SingleScreenVideoDialog
import com.example.floatingscreencasting.ui.screen.PlayerControlScreen
import com.example.floatingscreencasting.ui.model.*
import com.example.floatingscreencasting.ui.theme.FloatingScreenCastingTheme
import com.example.floatingscreencasting.history.PlaybackHistoryManager
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import com.example.floatingscreencasting.R
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Compose版本的主Activity
 * 使用Jetpack Compose Material 3构建UI
 */
class ComposeMainActivity : AppCompatActivity() {

    private lateinit var displayManager: DisplayManager
    private var videoPresentation: VideoPresentation? = null
    private var singleScreenDialog: SingleScreenVideoDialog? = null
    private lateinit var preferencesManager: PreferencesManager

    // 播放历史管理器（延迟初始化）
    private val historyManager: PlaybackHistoryManager by lazy {
        PlaybackHistoryManager.getInstance(this)
    }

    // DLNA服务
    private lateinit var dlnaService: DlnaRendererService

    // 音频输出控制器
    private lateinit var audioOutputController: AudioOutputController
    private lateinit var dlnaDmcClient: DlnaControlPoint
    private lateinit var phoneDeviceManager: PhoneDeviceManager
    private lateinit var webSocketServer: com.example.floatingscreencasting.websocket.CarWebSocketServer

    // 驾驶屏固定Display ID 2
    private val drivingDisplayId = 2

    // 进度更新：使用协程替代Handler
    private var progressUpdateJob: Job? = null
    private val progressUpdateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 屏幕设置弹窗状态
    private var showScreenSettingsDialog by mutableStateOf(false)

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
                dismissVideoWindow()
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
        // 不切换主题，保持启动主题让原生启动背景立即显示
        // Compose UI会覆盖在上面

        super.onCreate(savedInstanceState)

        // 设置透明状态栏
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        preferencesManager = PreferencesManager(this)

        // 注册广播接收器（轻量级操作，不会阻塞）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            registerReceiver(playbackErrorReceiver, IntentFilter("com.example.floatingscreencasting.PLAYBACK_ERROR"),
                Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackErrorReceiver, IntentFilter("com.example.floatingscreencasting.PLAYBACK_ERROR"))
        }
        EventBus.getDefault().register(this)

        // 设置Compose UI（直接显示主界面）
        setContent {
            FloatingScreenCastingTheme(
                darkTheme = true,
                modernDesign = true
            ) {
                PlayerControlScreen(
                    uiState = uiState.value,
                    showScreenSettingsDialog = showScreenSettingsDialog,
                    onToggleWindow = { toggleFloatingWindow() },
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
                    onContinueWatching = { continueWatching() },
                    onAudioOutputChange = { toggleAudioOutput() },
                    onScanDevices = { scanPhoneDevices() },
                    onRestartWebSocket = { restartWebSocketServer() },
                    onOpenSettingsPanel = { showScreenSettingsDialog = true },
                    onCloseSettingsPanel = { showScreenSettingsDialog = false }
                )
            }
        }

        // 后台异步初始化所有服务
        lifecycleScope.launch(Dispatchers.IO) {
            // 初始化音频输出控制器
            initializeAudioOutputController()

            // 初始化显示器
            initializeDisplays()

            // 加载设置
            loadSettings()

            // 初始化DLNA服务
            initializeDlnaService()

            // 更新继续观看状态
            updateContinueWatchingStatus()

            // 启动进度更新
            startProgressUpdate()
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

    // ==================== 面板控制方法 ====================

    /**
     * 打开调整面板
     */
    private fun openAdjustmentPanel() {
        showScreenSettingsDialog = true
    }

    /**
     * 关闭调整面板
     */
    private fun closeAdjustmentPanel() {
        showScreenSettingsDialog = false
    }

    /**
     * 切换悬浮窗启用状态
     */
    private fun toggleFloatingWindow() {
        val newState = !uiState.value.isFloatingWindowEnabled
        _uiState.value = uiState.value.copy(isFloatingWindowEnabled = newState)

        // 控制悬浮窗显示/隐藏
        if (newState) {
            videoPresentation?.show()
            _uiState.value = uiState.value.copy(isWindowVisible = true)
        } else {
            videoPresentation?.hide()
            _uiState.value = uiState.value.copy(isWindowVisible = false)
        }
    }

    private fun togglePlayPause() {
        val wasPlaying = uiState.value.isPlaying
        Log.d("ComposeMainActivity", "切换播放状态: 当前=$wasPlaying")

        // 检查是否有视频内容
        if (uiState.value.currentVideoTitle.isEmpty() && !wasPlaying) {
            // 没有视频内容且当前未播放，显示提示
            Toast.makeText(this, "请先投屏视频", Toast.LENGTH_SHORT).show()
            return
        }

        // 准备新的状态
        val newPlayingState = !wasPlaying
        val newCastingStatus = if (newPlayingState) {
            if (uiState.value.currentVideoTitle.isNotEmpty()) {
                "${uiState.value.currentVideoTitle} (正在投屏)"
            } else {
                "正在投屏"
            }
        } else {
            if (uiState.value.currentVideoTitle.isNotEmpty()) {
                "${uiState.value.currentVideoTitle} (已暂停)"
            } else {
                "已暂停"
            }
        }

        // 立即更新UI状态（乐观更新）
        _uiState.value = uiState.value.copy(
            isPlaying = newPlayingState,
            castingStatus = newCastingStatus
        )

        // 执行播放器操作（ExoPlayer方法是线程安全的）
        try {
            if (wasPlaying) {
                // 暂停播放，通知DLNA服务更新状态
                dlnaService.updateTransportState("PAUSED_PLAYBACK")
                videoPresentation?.pause()
            } else {
                // 恢复播放，通知DLNA服务更新状态
                dlnaService.updateTransportState("PLAYING")
                videoPresentation?.play()
            }
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "播放控制失败", e)
            // 恢复UI状态
            _uiState.value = uiState.value.copy(isPlaying = wasPlaying)
        }
    }

    private fun stop() {
        // 清除播放信息，更新为停止状态
        _uiState.value = uiState.value.copy(
            isPlaying = false,
            castingStatus = "停止播放",
            currentPosition = 0,
            duration = 0,
            currentVideoTitle = "",  // 清除视频标题
            currentVideoUrl = ""     // 清除视频URL
        )
        try {
            // 通知DLNA服务更新传输状态为STOPPED，让B站App知道播放已停止
            dlnaService.updateTransportState("STOPPED")
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
    }

    /**
     * 切换音频输出
     */
    private fun toggleAudioOutput() {
        Log.i("ComposeMainActivity", "========================================")
        Log.i("ComposeMainActivity", "toggleAudioOutput: 当前模式=${uiState.value.audioOutputMode}")
        lifecycleScope.launch {
            val currentMode = uiState.value.audioOutputMode
            val newMode = if (currentMode == "speaker") "phone" else "speaker"

            Log.i("ComposeMainActivity", "切换音频输出: $currentMode -> $newMode")

            // 切换音频输出模式
            val success = if (newMode == "phone") {
                // 切换到手机端
                Log.i("ComposeMainActivity", "调用switchOutputMode(PHONE)")
                // 检查是否有正在播放的视频（使用uiState而不是audioOutputController，确保状态同步）
                val currentUrl = uiState.value.currentVideoUrl
                val isPlaying = uiState.value.isPlaying

                Log.i("ComposeMainActivity", "当前视频URL: ${currentUrl.take(50)}..., 长度: ${currentUrl.length}")
                Log.i("ComposeMainActivity", "当前播放状态: $isPlaying")

                if (currentUrl.isEmpty() || !isPlaying) {
                    Log.e("ComposeMainActivity", "错误：没有正在播放的视频")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ComposeMainActivity,
                            "切换失败：请先投屏视频到车机",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                audioOutputController.switchOutputMode(
                    AudioOutputController.OutputMode.PHONE
                )
            } else {
                // 切换到车机扬声器
                Log.i("ComposeMainActivity", "调用switchOutputMode(SPEAKER)")
                audioOutputController.switchOutputMode(
                    AudioOutputController.OutputMode.SPEAKER
                )
            }

            Log.i("ComposeMainActivity", "切换结果: $success")
            if (success) {
                _uiState.value = uiState.value.copy(audioOutputMode = newMode)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ComposeMainActivity,
                        if (newMode == "phone") "音频输出已切换到手机端" else "音频输出已切换到车机扬声器",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ComposeMainActivity,
                        "切换失败，请查看日志了解详情",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            Log.i("ComposeMainActivity", "========================================")
        }
    }

    /**
     * 扫描手机设备
     * 注意：现在使用WebSocket连接，这个函数已废弃，仅为保留UI按钮
     */
    private fun scanPhoneDevices() {
        lifecycleScope.launch {
            Toast.makeText(
                this@ComposeMainActivity,
                "请使用手机连接车机的IP地址",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 启动WebSocket服务器
     */
    private fun startWebSocketServer() {
        try {
            Log.i("ComposeMainActivity", "正在启动WebSocket服务器，监听端口9999...")
            webSocketServer.start()
            Log.i("ComposeMainActivity", "WebSocket服务器已启动，监听端口9999")
            Log.i("ComposeMainActivity", "WebSocket服务器绑定地址: ${webSocketServer.address}")

            // 更新UI状态
            lifecycleScope.launch(Dispatchers.Main) {
                _uiState.value = uiState.value.copy(isWebSocketServerRunning = true)
            }
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "WebSocket服务器启动失败", e)
            // 更新UI状态
            lifecycleScope.launch(Dispatchers.Main) {
                _uiState.value = uiState.value.copy(isWebSocketServerRunning = false)
            }
            throw e
        }
    }

    /**
     * 启动WebSocket服务器监控线程
     * 每5秒检查一次WebSocket服务器状态，如果停止了就自动重启
     */
    private fun startWebSocketMonitor() {
        val monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) { // 无限循环监控
                try {
                    // 检查WebSocket服务器是否在运行
                    val isRunning = try {
                        webSocketServer.isRunning
                    } catch (e: Exception) {
                        false
                    }

                    // 更新UI状态
                    lifecycleScope.launch(Dispatchers.Main) {
                        _uiState.value = uiState.value.copy(isWebSocketServerRunning = isRunning)
                    }

                    if (!isRunning) {
                        Log.w("ComposeMainActivity", "WebSocket服务器已停止，尝试重新启动...")
                        try {
                            startWebSocketServer()
                            Log.i("ComposeMainActivity", "WebSocket服务器自动重启成功")

                            // 重新设置回调
                            setupWebSocketCallbacks()
                        } catch (e: Exception) {
                            Log.e("ComposeMainActivity", "WebSocket服务器自动重启失败", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "WebSocket监控检查失败", e)
                }

                // 每5秒检查一次
                delay(5000)
            }
        }
        Log.i("ComposeMainActivity", "WebSocket监控线程已启动")
    }

    /**
     * 设置WebSocket回调
     */
    private fun setupWebSocketCallbacks() {
        webSocketServer.onClientConnected = { clientId ->
            Log.i("ComposeMainActivity", "手机端已连接: $clientId")
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@ComposeMainActivity, "FSCast Remote已连接", Toast.LENGTH_SHORT).show()
                _uiState.value = uiState.value.copy(
                    connectedPhoneDevice = "FSCast Remote",
                    webSocketClientCount = 1
                )
            }
        }

        webSocketServer.onClientDisconnected = { clientId ->
            Log.i("ComposeMainActivity", "手机端已断开: $clientId")
            lifecycleScope.launch(Dispatchers.Main) {
                // 如果当前是手机模式，自动切换回扬声器模式
                if (audioOutputController.getCurrentMode() == AudioOutputController.OutputMode.PHONE) {
                    Log.i("ComposeMainActivity", "手机端断开，自动切换回扬声器模式")
                    audioOutputController.switchOutputMode(AudioOutputController.OutputMode.SPEAKER)
                    // 同步更新UI状态，确保显示和实际状态一致
                    _uiState.value = uiState.value.copy(audioOutputMode = "speaker")
                }
                Toast.makeText(this@ComposeMainActivity, "FSCast Remote已断开，已切换回扬声器", Toast.LENGTH_SHORT).show()
                _uiState.value = uiState.value.copy(
                    connectedPhoneDevice = null,
                    webSocketClientCount = 0
                )
            }
        }

        // 设置控制命令回调（处理来自手机端的播放控制命令）
        webSocketServer.onControlCommand = { action, data, syncId, clientId ->
            Log.i("ComposeMainActivity", "收到手机端控制命令: action=$action, syncId=$syncId, clientId=$clientId")
            lifecycleScope.launch {
                try {
                    // 转发到AudioOutputController处理
                    audioOutputController.executeRemoteCommand(action, data, syncId)
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "执行远程命令失败: ${e.message}")
                }
            }
        }

        // 处理来自手机端的消息
        webSocketServer.onMessageReceived = { message, ws ->
            try {
                val json = org.json.JSONObject(message)
                val type = json.getString("type")

                when (type) {
                    "state_update" -> {
                        // 手机端播放状态更新
                        val data = json.getJSONObject("data")
                        val position = data.getLong("position")
                        val duration = data.getLong("duration")
                        val isPlaying = data.getBoolean("isPlaying")

                        Log.i("ComposeMainActivity", "手机端状态更新: ${position}ms/${duration}ms, playing=$isPlaying")
                        // TODO: 更新UI状态显示手机端播放状态
                    }
                    "error" -> {
                        // 手机端错误报告
                        val data = json.getJSONObject("data")
                        val errorMessage = data.getString("message")

                        Log.e("ComposeMainActivity", "手机端错误: $errorMessage")
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(this@ComposeMainActivity, "手机端错误: $errorMessage", Toast.LENGTH_LONG).show()
                            // 自动切换回扬声器模式
                            audioOutputController.switchOutputMode(AudioOutputController.OutputMode.SPEAKER)
                            // 同步更新UI状态
                            _uiState.value = uiState.value.copy(audioOutputMode = "speaker")
                        }
                    }
                    "connected" -> {
                        // 手机端连接确认（可选，已在onClientConnected中处理）
                        val data = json.getJSONObject("data")
                        val deviceType = data.getString("deviceType")
                        Log.i("ComposeMainActivity", "手机端设备类型: $deviceType")
                    }
                    else -> {
                        Log.d("ComposeMainActivity", "收到未知消息类型: $type")
                    }
                }
            } catch (e: Exception) {
                Log.e("ComposeMainActivity", "解析手机端消息失败: ${e.message}")
            }
        }

        // 检查是否已有客户端连接（用于UI启动时的状态同步）
        lifecycleScope.launch(Dispatchers.Main) {
            val currentClientCount = webSocketServer.getClientCount()
            if (currentClientCount > 0) {
                Log.i("ComposeMainActivity", "检测到已有 $currentClientCount 个客户端连接")
                _uiState.value = uiState.value.copy(
                    connectedPhoneDevice = "FSCast Remote",
                    webSocketClientCount = currentClientCount
                )
            }
        }
    }

    /**
     * 清理旧的FSCast进程（避免端口占用）
     * 增强版：多次检查，确保端口完全释放
     */
    private suspend fun cleanupOldFSCastProcesses() {
        try {
            // 获取当前进程ID和包名
            val myPid = android.os.Process.myPid()
            val myPackageName = packageName
            Log.i("ComposeMainActivity", "========================================")
            Log.i("ComposeMainActivity", "清理旧FSCast进程（增强版）")
            Log.i("ComposeMainActivity", "当前进程ID: $myPid, 包名: $myPackageName")
            Log.i("ComposeMainActivity", "========================================")

            // 查找所有FSCast进程
            val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            var pass = 0
            var foundOldProcesses = true

            // 最多清理3次，确保所有旧进程都被杀死
            while (foundOldProcesses && pass < 3) {
                pass++
                Log.i("ComposeMainActivity", "清理第 $pass 轮")

                val runningProcesses = activityManager.runningAppProcesses
                val oldProcesses = mutableListOf<Int>()

                for (processInfo in runningProcesses) {
                    if (processInfo.processName != null &&
                        processInfo.processName.contains("floatingscreencasting")) {
                        val pid = processInfo.pid
                        // 跳过当前进程
                        if (pid != myPid) {
                            oldProcesses.add(pid)
                            Log.i("ComposeMainActivity", "发现旧FSCast进程: $pid (${processInfo.processName})")
                        }
                    }
                }

                // 杀死所有旧进程
                if (oldProcesses.isNotEmpty()) {
                    Log.i("ComposeMainActivity", "发现 ${oldProcesses.size} 个旧FSCast进程，准备清理")
                    oldProcesses.forEach { pid ->
                        try {
                            Log.i("ComposeMainActivity", "杀死旧进程: $pid")
                            android.os.Process.killProcess(pid)
                        } catch (e: Exception) {
                            Log.w("ComposeMainActivity", "杀死进程 $pid 失败: ${e.message}")
                        }
                    }

                    // 每次杀死后等待1秒
                    delay(1000)
                    foundOldProcesses = true
                } else {
                    Log.i("ComposeMainActivity", "第 $pass 轮：没有发现旧FSCast进程")
                    foundOldProcesses = false
                }
            }

            // 额外等待时间，确保TCP端口完全释放（TIME_WAIT状态）
            Log.i("ComposeMainActivity", "等待TCP端口完全释放...")
            delay(3000)
            Log.i("ComposeMainActivity", "旧进程清理完成，端口已释放")
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "清理旧进程失败", e)
        }
    }

    /**
     * 重启WebSocket服务器
     */
    private fun restartWebSocketServer() {
        // 先在主线程显示Toast
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(
                this@ComposeMainActivity,
                "正在重启WebSocket服务器...",
                Toast.LENGTH_SHORT
            ).show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 步骤1: 清理旧FSCast进程（包含等待端口释放）
                cleanupOldFSCastProcesses()

                // 步骤2: 停止现有的WebSocket服务器
                if (::webSocketServer.isInitialized) {
                    Log.i("ComposeMainActivity", "停止现有的WebSocket服务器")
                    webSocketServer.stop()
                    delay(2000) // 等待服务器完全停止和端口释放

                    // 更新UI状态为停止
                    lifecycleScope.launch(Dispatchers.Main) {
                        _uiState.value = uiState.value.copy(isWebSocketServerRunning = false)
                    }
                }

                // 步骤3: 创建新的WebSocket服务器实例
                Log.i("ComposeMainActivity", "创建新的WebSocket服务器实例")
                webSocketServer = com.example.floatingscreencasting.websocket.CarWebSocketServer(9999)

                // 步骤4: 启动WebSocket服务器
                startWebSocketServer()

                // 步骤5: 重新设置回调
                setupWebSocketCallbacks()

                // 步骤6: 更新AudioOutputController的WebSocket服务器引用
                audioOutputController.updateWebSocketServer(webSocketServer)
                Log.i("ComposeMainActivity", "已更新AudioOutputController的WebSocket服务器引用")

                // 在主线程显示成功消息
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ComposeMainActivity,
                        "WebSocket服务器重启成功",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("ComposeMainActivity", "重启WebSocket服务器失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ComposeMainActivity,
                        "重启失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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
            DisplayInfo(id = display.displayId, name = display.name)
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
            Log.d("ComposeMainActivity", "开始创建视频窗口，displayId: ${display.displayId}")

            // 检查是否是主屏幕（单屏幕设备）
            if (display.displayId == Display.DEFAULT_DISPLAY) {
                // 单屏幕设备，使用Dialog
                Log.d("ComposeMainActivity", "单屏幕设备，使用SingleScreenVideoDialog")
                SingleScreenVideoDialog(this).apply {
                    singleScreenDialog = this
                    show()
                    Log.d("ComposeMainActivity", "Dialog显示成功")

                    // 初始静音（视频只播放画面）
                    setMuted(true)
                }

                _uiState.value = uiState.value.copy(isWindowVisible = true)
                Log.d("ComposeMainActivity", "单屏幕视频窗口创建成功")
                return
            }

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

    /**
     * 初始化音频输出控制器
     */
    private fun initializeAudioOutputController() {
        // 初始化DLNA DMC客户端（用于发现和控制手机设备）
        dlnaDmcClient = DlnaControlPoint(this)

        // 初始化手机设备管理器
        phoneDeviceManager = PhoneDeviceManager(this)

        // 初始化WebSocket服务器（使用端口9999）
        // 明确绑定到IPv4地址 0.0.0.0
        webSocketServer = com.example.floatingscreencasting.websocket.CarWebSocketServer(9999)

        // 立即启动WebSocket服务器
        lifecycleScope.launch(Dispatchers.IO) {
            // 步骤1: 清理旧FSCast进程（避免端口占用）
            cleanupOldFSCastProcesses()

            // 步骤2: 启动WebSocket服务器
            startWebSocketServer()

            // 步骤3: 设置WebSocket回调（必须在启动后立即设置）
            setupWebSocketCallbacks()

            // 步骤4: 启动WebSocket监控线程，自动重启
            startWebSocketMonitor()
        }

        // 初始化音频输出控制器（传入WebSocket服务器）
        audioOutputController = AudioOutputController(dlnaDmcClient, phoneDeviceManager, webSocketServer)

        // 设置静音控制回调
        audioOutputController.setMuteControlCallback(object : AudioOutputController.MuteControlCallback {
            override fun setMuted(muted: Boolean) {
                // 通知VideoPresentation静音/取消静音
                videoPresentation?.setMuted(muted)
                Log.d("ComposeMainActivity", "音频输出控制器请求静音: $muted")
            }
        })

        // 设置播放状态监听器
        audioOutputController.setPlaybackStateListener(object : AudioOutputController.PlaybackStateListener {
            override fun onPlay() {
                videoPresentation?.play()
            }

            override fun onPause() {
                videoPresentation?.pause()
            }

            override fun onSeek(positionMs: Long) {
                videoPresentation?.seekTo(positionMs)
            }

            override fun onStop() {
                videoPresentation?.stop()
            }

            override fun getCurrentPosition(): Long {
                val position = videoPresentation?.getExoPlayer()?.currentPosition ?: 0L
                Log.d("ComposeMainActivity", "getCurrentPosition调用: videoPresentation=${videoPresentation != null}, exoPlayer=${videoPresentation?.getExoPlayer() != null}, position=$position")
                return position
            }
        })

        // 启动DLNA DMC客户端（开始发现手机设备）
        dlnaDmcClient.start()

        Log.d("ComposeMainActivity", "音频输出控制器初始化完成")
    }

    private fun initializeDlnaService() {
        dlnaService = DlnaRendererService.getInstance(this)

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

            onPlayMedia = { uri, headers ->
                Log.d("ComposeMainActivity", "收到onPlayMedia回调: $uri")
                Log.d("ComposeMainActivity", "HTTP头: $headers")
                // 保存视频URI到AudioOutputController，以便后续切换音频输出时使用
                audioOutputController.setCurrentVideoUri(uri, headers)
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        Log.d("ComposeMainActivity", "videoPresentation是否为null: ${videoPresentation == null}")
                        videoPresentation?.playMedia(uri)
                        if (uri.isNotEmpty()) {
                            dlnaService.updateTransportState("PLAYING")
                            // 提取视频标题
                            val title = extractVideoTitle(uri)
                            // 播放开始，更新视频信息
                            _uiState.value = uiState.value.copy(
                                isPlaying = true,
                                currentVideoUrl = uri,
                                currentVideoTitle = title
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("ComposeMainActivity", "播放视频失败", e)
                    }
                }
            }

            onPlayMediaWithMetadata = { uri, headers, title, durationMs, initialPositionMs ->
                Log.d("ComposeMainActivity", "收到onPlayMediaWithMetadata回调")
                Log.d("ComposeMainActivity", "URI: $uri")
                Log.d("ComposeMainActivity", "标题: $title")
                Log.d("ComposeMainActivity", "时长: ${durationMs}ms")
                Log.d("ComposeMainActivity", "初始位置: ${initialPositionMs}ms")
                Log.d("ComposeMainActivity", "HTTP头: $headers")
                // 保存视频URI到AudioOutputController，以便后续切换音频输出时使用
                audioOutputController.setCurrentVideoUri(uri, headers)
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        Log.d("ComposeMainActivity", "videoPresentation是否为null: ${videoPresentation == null}")
                        videoPresentation?.playMedia(uri, title, durationMs, initialPositionMs)
                        if (uri.isNotEmpty()) {
                            dlnaService.updateTransportState("PLAYING")
                            // 使用metadata中的标题，处理B站占位符：空、"video"、"studio_video_xxx"
                            val finalTitle = if (title.isBlank() || title == "video" ||
                                title.startsWith("studio_video_") || title.matches(Regex("studio_video_\\d+"))) {
                                extractVideoTitle(uri)
                            } else {
                                title
                            }
                            // 播放开始，更新视频信息
                            _uiState.value = uiState.value.copy(
                                isPlaying = true,
                                currentVideoUrl = uri,
                                currentVideoTitle = finalTitle,
                                duration = durationMs
                            )
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
                    // 停止播放，恢复到初始状态，清除所有播放信息（和车机端stop按钮一样）
                    videoPresentation?.resetToInitialState()
                    _uiState.value = uiState.value.copy(
                        isPlaying = false,
                        castingStatus = "等待投屏",
                        currentPosition = 0,
                        duration = 0,
                        currentVideoTitle = "",  // 清除视频标题
                        currentVideoUrl = ""     // 清除视频URL
                    )
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "停止播放失败", e)
                }
            }

            onPauseMedia = {
                Log.d("ComposeMainActivity", "收到暂停回调")
                try {
                    videoPresentation?.pause()
                    // 暂停播放，保留播放信息，状态显示"已暂停"
                    _uiState.value = uiState.value.copy(
                        isPlaying = false,
                        castingStatus = if (uiState.value.currentVideoTitle.isNotEmpty()) {
                            "${uiState.value.currentVideoTitle} (已暂停)"
                        } else {
                            "已暂停"
                        }
                    )
                } catch (e: Exception) {
                    Log.e("ComposeMainActivity", "暂停播放失败", e)
                }
            }

            onPlay = {
                Log.d("ComposeMainActivity", "收到恢复播放回调")
                try {
                    videoPresentation?.play()
                    // 恢复播放，状态显示"正在投屏"或视频标题
                    _uiState.value = uiState.value.copy(
                        isPlaying = true,
                        castingStatus = if (uiState.value.currentVideoTitle.isNotEmpty()) {
                            "${uiState.value.currentVideoTitle} (正在投屏)"
                        } else {
                            "正在投屏"
                        }
                    )
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

    /**
     * 初始化音频流管理器
     */
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
        singleScreenDialog?.apply {
            windowX = savedSettings.x
            windowY = savedSettings.y
            windowWidth = savedSettings.width
            windowHeight = savedSettings.height
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
                    // 只有在有视频内容时才更新进度
                    if (_uiState.value.currentVideoTitle.isNotEmpty()) {
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
                                // 注意：进度同步由AudioOutputController内部的定时器自动处理，不需要在这里调用
                            }
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackEndEvent(event: PlaybackEndEvent) {
        Log.i("ComposeMainActivity", "收到播放结束事件，位置: ${event.position}ms")

        // 更新UI状态
        _uiState.value = uiState.value.copy(
            isPlaying = false,
            castingStatus = "播放结束"
        )

        // 如果在手机模式，发送停止命令到手机
        lifecycleScope.launch {
            if (audioOutputController.getCurrentMode() == AudioOutputController.OutputMode.PHONE) {
                Log.i("ComposeMainActivity", "手机模式：发送停止命令到手机端")
                audioOutputController.stop()
            }
        }
    }

    // ==================== 生命周期 ====================

    override fun onDestroy() {
        super.onDestroy()
        // 🔧 修复内存泄漏：取消协程作用域
        progressUpdateScope.coroutineContext[Job]?.cancel()
        stopProgressUpdate()
        displayManager.unregisterDisplayListener(displayListener)
        unregisterReceiver(playbackErrorReceiver)
        EventBus.getDefault().unregister(this)

        // 停止并释放音频输出控制器
        audioOutputController.release()
        dlnaDmcClient.stop()

        // 停止WebSocket服务器
        try {
            if (::webSocketServer.isInitialized) {
                webSocketServer.stop()
            }
        } catch (e: Exception) {
            Log.e("ComposeMainActivity", "停止WebSocket服务器失败", e)
        }

        videoPresentation?.dismiss()
    }

    // ==================== 辅助方法 ====================

    private fun isDrivingDisplay(display: Display): Boolean {
        return display.displayId == drivingDisplayId
    }

    /**
     * 从视频URI中提取标题
     */
    private fun extractVideoTitle(uri: String): String {
        return when {
            uri.contains("bilibili") -> "哔哩哔哩"
            uri.contains("iqiyi") -> "爱奇艺"
            uri.contains("v.qq.com") -> "腾讯视频"
            uri.contains("youku") -> "优酷"
            uri.contains("mgtv") -> "芒果TV"
            else -> "在线视频"
        }
    }

    private fun isPresentationDisplay(display: Display): Boolean {
        // 如果设备只有一个屏幕，允许使用内置屏幕
        val displays = displayManager.displays
        if (displays.size == 1) {
            return true
        }
        // 多屏幕设备，只允许使用Presentation屏幕
        return display.flags and Display.FLAG_PRESENTATION != 0
    }

    /**
     * 统一的视频播放控制方法
     */
    private fun playVideo(uri: String) {
        videoPresentation?.playMedia(uri)
        singleScreenDialog?.playMedia(uri)
    }

    private fun pauseVideo() {
        videoPresentation?.pause()
        singleScreenDialog?.pause()
    }

    private fun stopVideo() {
        videoPresentation?.stop()
        singleScreenDialog?.stop()
    }

    private fun seekVideo(position: Long) {
        videoPresentation?.seekTo(position)
        singleScreenDialog?.seekTo(position)
    }

    private fun setMuted(muted: Boolean) {
        videoPresentation?.setMuted(muted)
        singleScreenDialog?.setMuted(muted)
    }

    private fun getVideoPlayer(): ExoPlayer? {
        return videoPresentation?.getExoPlayer() ?: singleScreenDialog?.getExoPlayer()
    }

    private fun isVideoPlaying(): Boolean {
        return videoPresentation?.isPlaying() == true || singleScreenDialog?.isPlaying() == true
    }

    private fun dismissVideoWindow() {
        videoPresentation?.dismiss()
        videoPresentation = null
        singleScreenDialog?.dismiss()
        singleScreenDialog = null
    }

    private fun addressToString(address: ByteArray): String {
        return address.joinToString(":") { String.format("%02X", it) }
    }
}
