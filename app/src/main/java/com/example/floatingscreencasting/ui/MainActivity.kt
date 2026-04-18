package com.example.floatingscreencasting.ui

import android.app.AlertDialog
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.example.floatingscreencasting.R
import com.example.floatingscreencasting.databinding.ActivityMainBinding
import com.example.floatingscreencasting.dlna.AudioOutputController
import com.example.floatingscreencasting.dlna.PhoneDeviceManager
import com.example.floatingscreencasting.data.remote.dlna.DlnaControlPoint
import com.example.floatingscreencasting.data.remote.dlna.DlnaRendererService
import com.example.floatingscreencasting.events.MuteEvent
import com.example.floatingscreencasting.presentation.VideoPresentation
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * 中控屏主界面
 * 三板块布局：悬浮窗控制、播放控制、悬浮窗设置
 */
class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private lateinit var displayManager: DisplayManager
    private var videoPresentation: VideoPresentation? = null
    private lateinit var preferencesManager: PreferencesManager

    // ExoPlayer实例 - 在MainActivity中创建和管理
    private var exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null

    // 驾驶屏固定Display ID 2
    private val drivingDisplayId = 2

    // DLNA服务
    private lateinit var dlnaService: DlnaRendererService

    // 音频输出控制器
    private lateinit var audioOutputController: AudioOutputController
    private lateinit var dlnaDmcClient: DlnaControlPoint
    private lateinit var phoneDeviceManager: PhoneDeviceManager
    private lateinit var webSocketServer: com.example.floatingscreencasting.websocket.CarWebSocketServer

    // 当前选中的比例
    private var currentAspectRatio = "16:9"

    // 静音状态
    private var isMuted = true  // 默认静音

    // 进度更新定时器
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 1000)
        }
    }

    // 播放错误广播接收器
    private val playbackErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val error = intent?.getStringExtra("error")
            val errorCode = intent?.getIntExtra("errorCode", 0) ?: 0

            Log.e("MainActivity", "收到播放错误: $error (代码: $errorCode)")

            // 更新UI显示错误状态
            updateCastingStatus(false)

            // 显示错误提示
            when {
                error?.contains("959") == true -> {
                    Toast.makeText(
                        this@MainActivity,
                        "Bilibili视频播放失败（959错误）\n建议: 使用其他平台投屏",
                        Toast.LENGTH_LONG
                    ).show()
                }
                error?.contains("403") == true -> {
                    Toast.makeText(
                        this@MainActivity,
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
                        this@MainActivity,
                        errorMsg,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

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
                updateCastingStatus(false)
                updateToggleButton()
            }
        }

        override fun onDisplayChanged(displayId: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置透明状态栏，让内容延伸到状态栏下方
        window.setDecorFitsSystemWindows(false)

        // 设置状态栏为透明，并确保文字可见
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // 设置导航栏为透明
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = PreferencesManager(this)

        setupToolbar()
        initializeDisplays()
        initializePlayer()  // 初始化ExoPlayer
        loadSettings()
        setupControls()
        setupAspectRatioButtons()
        setupQuickActions()

        // 注册播放错误广播接收器
        registerReceiver(playbackErrorReceiver, IntentFilter("com.example.floatingscreencasting.PLAYBACK_ERROR"))

        // 注册EventBus
        EventBus.getDefault().register(this)

        // 初始化静音按钮状态
        updateMuteButton()

        // 初始化音频输出控制器
        initializeAudioOutputController()

        // 初始化并启动DLNA服务
        initializeDlnaService()

        // 启动进度更新
        progressHandler.post(progressRunnable)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    /**
     * 初始化ExoPlayer - 在MainActivity中创建
     * 这样ExoPlayer运行在主Activity的Context中，音频路由应该正常
     */
    private fun initializePlayer() {
        // 创建正确的AudioAttributes - 使用MUSIC类型以支持蓝牙A2DP
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        Log.d("MainActivity", "初始化ExoPlayer - USAGE_MEDIA, CONTENT_TYPE_MUSIC")

        // 创建HTTP数据源工厂
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("MiTV/1.0 (Linux;Android 12) MI_TV_4")
            .setConnectTimeoutMs(30 * 1000)
            .setReadTimeoutMs(30 * 1000)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            this,
            httpDataSourceFactory
        )

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        // 在MainActivity的Context中创建ExoPlayer
        exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)  // true = handleAudioFocus
            .setHandleAudioBecomingNoisy(true)
            .build()

        Log.d("MainActivity", "ExoPlayer创建完成: $exoPlayer")
    }

    /**
     * 加载保存的设置
     */
    private fun loadSettings() {
        val savedSettings = preferencesManager.windowPosition
        currentAspectRatio = savedSettings.aspectRatio

        // 更新滑块值，确保在范围内
        binding.positionXSlider.value = savedSettings.x.toFloat().coerceIn(binding.positionXSlider.valueFrom, binding.positionXSlider.valueTo)
        binding.positionYSlider.value = savedSettings.y.toFloat().coerceIn(binding.positionYSlider.valueFrom, binding.positionYSlider.valueTo)
        binding.sizeSlider.value = savedSettings.width.toFloat().coerceIn(binding.sizeSlider.valueFrom, binding.sizeSlider.valueTo)
        binding.alphaSlider.value = savedSettings.alpha.toFloat().coerceIn(binding.alphaSlider.valueFrom, binding.alphaSlider.valueTo)

        // 更新显示值
        updatePositionDisplay(
            binding.positionXSlider.value.toInt(),
            binding.positionYSlider.value.toInt()
        )
        val height = preferencesManager.calculateHeightForWidth(
            binding.sizeSlider.value.toInt(),
            currentAspectRatio
        )
        updateSizeDisplay(binding.sizeSlider.value.toInt(), height)
        updateAlphaDisplay(binding.alphaSlider.value.toInt())
        updateAspectRatioButtons()

        // 应用到VideoPresentation
        videoPresentation?.apply {
            windowX = binding.positionXSlider.value.toInt()
            windowY = binding.positionYSlider.value.toInt()
            windowWidth = binding.sizeSlider.value.toInt()
            windowHeight = height
            windowAlpha = binding.alphaSlider.value / 100f
        }

        updateToggleButton()
    }

    /**
     * 保存当前设置
     */
    private fun saveSettings() {
        val width = binding.sizeSlider.value.toInt()
        val height = preferencesManager.calculateHeightForWidth(width, currentAspectRatio)
        val x = binding.positionXSlider.value.toInt()
        val y = binding.positionYSlider.value.toInt()
        val alpha = binding.alphaSlider.value.toInt()

        val settings = PreferencesManager.WindowPosition(
            x = x,
            y = y,
            width = width,
            height = height,
            alpha = alpha,
            aspectRatio = currentAspectRatio
        )

        preferencesManager.windowPosition = settings
    }

    /**
     * 初始化显示设备
     */
    private fun initializeDisplays() {
        displayManager = getSystemService(DisplayManager::class.java)

        // 列出所有可用的显示设备
        val allDisplays = displayManager.displays
        Log.d("MainActivity", "系统共有 ${allDisplays.size} 个显示设备:")
        allDisplays.forEach { display ->
            Log.d("MainActivity", "  Display ID: ${display.displayId}, Name: ${display.name}, Flags: ${display.flags}")
        }

        // 尝试查找合适的显示设备
        var targetDisplay: Display? = null

        // 1. 优先使用驾驶屏 Display ID 2
        targetDisplay = displayManager.getDisplay(drivingDisplayId)
        if (targetDisplay != null) {
            Log.d("MainActivity", "找到驾驶屏 Display ID 2")
        } else {
            // 2. 查找Presentation显示设备
            val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            if (presentationDisplays.isNotEmpty()) {
                targetDisplay = presentationDisplays[0]
                Log.d("MainActivity", "找到Presentation显示设备: ID ${targetDisplay.displayId}")
            } else {
                // 3. 使用任何非主显示的设备
                allDisplays.forEach { display ->
                    if (display.displayId != Display.DEFAULT_DISPLAY) {
                        targetDisplay = display
                        Log.d("MainActivity", "找到非主显示设备: ID ${display.displayId}")
                        return@forEach
                    }
                }
            }
        }

        if (targetDisplay != null) {
            showPresentationOnDisplay(targetDisplay)
        } else {
            Log.e("MainActivity", "未找到可用的显示设备")
            Toast.makeText(this, "未检测到驾驶屏，请在设置中检查显示设备", Toast.LENGTH_LONG).show()
        }

        // 注册显示监听
        displayManager.registerDisplayListener(displayListener, null)
    }

    /**
     * 判断是否为Presentation显示
     */
    private fun isPresentationDisplay(display: Display): Boolean {
        return display.flags and Display.FLAG_PRESENTATION == Display.FLAG_PRESENTATION
    }

    /**
     * 判断是否为驾驶屏
     */
    private fun isDrivingDisplay(display: Display): Boolean {
        return display.displayId == drivingDisplayId
    }

    /**
     * 在指定显示上显示Presentation
     */
    private fun showPresentationOnDisplay(display: Display) {
        // 关闭旧的Presentation
        if (videoPresentation != null) {
            videoPresentation?.dismiss()
            videoPresentation = null
        }

        try {
            videoPresentation = VideoPresentation(this, display).apply {
                show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "创建 Presentation 失败", e)
            Toast.makeText(this, "创建浮窗失败: ${e.message}", Toast.LENGTH_LONG).show()
        }

        updateCastingStatus(false)
        updateToggleButton()

        // 应用保存的设置（确保在有效范围内）
        val savedSettings = preferencesManager.windowPosition
        val validX = savedSettings.x.coerceIn(0, 1920)
        val validY = savedSettings.y.coerceIn(0, 720)
        val validWidth = savedSettings.width.coerceIn(160, 1920)
        val validHeight = preferencesManager.calculateHeightForWidth(validWidth, currentAspectRatio)

        videoPresentation?.apply {
            windowX = validX
            windowY = validY
            windowWidth = validWidth
            windowHeight = validHeight
            windowAlpha = (savedSettings.alpha.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
        }
    }

    /**
     * 设置控制面板
     */
    private fun setupControls() {
        // 显示/隐藏悬浮窗按钮
        binding.toggleWindowButton.setOnClickListener {
            if (videoPresentation != null) {
                if (videoPresentation?.isShowing == true) {
                    videoPresentation?.dismiss()
                    updateCastingStatus(false)
                } else {
                    // 重新显示
                    val display = displayManager.getDisplay(drivingDisplayId)
                        ?: displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
                    display?.let { showPresentationOnDisplay(it) }
                }
            }
            updateToggleButton()
        }

        // 位置控制
        binding.positionXSlider.addOnChangeListener { _, value, _ ->
            val x = value.toInt()
            val y = binding.positionYSlider.value.toInt()
            updatePositionDisplay(x, y)
            videoPresentation?.windowX = x
            videoPresentation?.windowY = y
            saveSettings()
        }

        binding.positionYSlider.addOnChangeListener { _, value, _ ->
            val x = binding.positionXSlider.value.toInt()
            val y = value.toInt()
            updatePositionDisplay(x, y)
            videoPresentation?.windowX = x
            videoPresentation?.windowY = y
            saveSettings()
        }

        // 大小控制
        binding.sizeSlider.addOnChangeListener { _, value, _ ->
            val width = value.toInt()
            val height = preferencesManager.calculateHeightForWidth(width, currentAspectRatio)
            updateSizeDisplay(width, height)
            videoPresentation?.windowWidth = width
            videoPresentation?.windowHeight = height
            saveSettings()
        }

        // 透明度控制
        binding.alphaSlider.addOnChangeListener { _, value, _ ->
            val alpha = value.toInt()
            updateAlphaDisplay(alpha)
            videoPresentation?.windowAlpha = alpha / 100f
            saveSettings()
        }

        // 播放控制
        binding.playPauseButton.setOnClickListener {
            val isPlaying = videoPresentation?.isPlaying() == true
            if (isPlaying) {
                binding.playPauseButton.setIconResource(R.drawable.ic_play)
                videoPresentation?.pause()
            } else {
                binding.playPauseButton.setIconResource(R.drawable.ic_pause)
                videoPresentation?.play()
            }
        }

        binding.previousButton.setOnClickListener {
            videoPresentation?.previous()
        }

        binding.nextButton.setOnClickListener {
            videoPresentation?.next()
        }

        binding.stopButton.setOnClickListener {
            videoPresentation?.stop()
        }

        // 静音按钮
        binding.muteButton.setOnClickListener {
            isMuted = !isMuted
            updateMuteButton()

            // 通过EventBus发送静音状态
            EventBus.getDefault().post(MuteEvent(isMuted))

            // 应用静音状态到VideoPresentation
            videoPresentation?.setMuted(isMuted)
        }

        // 进度条控制
        binding.progressSlider.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                // 用户开始拖动，暂停进度更新
            }

            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                // 用户释放滑块，执行seek
                val position = slider.value.toLong()
                videoPresentation?.seekTo(position)
            }
        })
    }

    /**
     * 更新播放进度
     */
    private fun updateProgress() {
        val player = videoPresentation?.getExoPlayer()
        player?.let {
            if (it.playbackState != Player.STATE_IDLE) {
                val currentPosition = it.currentPosition
                val duration = it.duration
                val isPlaying = it.isPlaying

                if (duration > 0) {
                    binding.progressSlider.valueTo = duration.toFloat()
                    binding.progressSlider.value = currentPosition.toFloat()

                    binding.currentPositionText.text = formatTime(currentPosition)
                    binding.durationText.text = formatTime(duration)
                }
                // 注意：进度同步由AudioOutputController内部的定时器自动处理，不需要在这里调用
            }
        }
    }

    /**
     * 格式化时间
     */
    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 设置比例选择按钮
     */
    private fun setupAspectRatioButtons() {
        binding.ratio169Button.setOnClickListener {
            currentAspectRatio = "16:9"
            onAspectRatioChanged()
        }

        binding.ratio43Button.setOnClickListener {
            currentAspectRatio = "4:3"
            onAspectRatioChanged()
        }

        binding.ratioPortraitButton.setOnClickListener {
            currentAspectRatio = "9:16"
            onAspectRatioChanged()
        }
    }

    /**
     * 比例变化时的处理
     */
    private fun onAspectRatioChanged() {
        updateAspectRatioButtons()

        val width = binding.sizeSlider.value.toInt()
        val height = preferencesManager.calculateHeightForWidth(width, currentAspectRatio)

        updateSizeDisplay(width, height)
        videoPresentation?.windowWidth = width
        videoPresentation?.windowHeight = height
        saveSettings()
    }

    /**
     * 更新比例按钮状态
     */
    private fun updateAspectRatioButtons() {
        val selectedColor = ContextCompat.getColorStateList(this, R.color.purple_500)
        val defaultColor = ContextCompat.getColorStateList(this, android.R.color.transparent)

        binding.ratio169Button.strokeColor = if (currentAspectRatio == "16:9") selectedColor else defaultColor
        binding.ratio43Button.strokeColor = if (currentAspectRatio == "4:3") selectedColor else defaultColor
        binding.ratioPortraitButton.strokeColor = if (currentAspectRatio == "9:16") selectedColor else defaultColor
    }

    /**
     * 设置快捷操作按钮
     */
    private fun setupQuickActions() {
        binding.centerButton.setOnClickListener {
            val displayWidth = 1920
            val displayHeight = 720
            val width = binding.sizeSlider.value.toInt()
            val height = preferencesManager.calculateHeightForWidth(width, currentAspectRatio)

            val x = (displayWidth - width) / 2
            val y = (displayHeight - height) / 2

            binding.positionXSlider.value = x.toFloat()
            binding.positionYSlider.value = y.toFloat()
        }

        binding.maximizeButton.setOnClickListener {
            val maxWidth = 1920
            val maxHeight = 720

            val width = when (currentAspectRatio) {
                "16:9" -> maxWidth
                "4:3" -> maxWidth
                "9:16" -> (maxHeight * 9) / 16
                else -> maxWidth
            }

            val height = preferencesManager.calculateHeightForWidth(width, currentAspectRatio)

            binding.sizeSlider.value = width.toFloat()
            binding.positionXSlider.value = 0f
            binding.positionYSlider.value = ((maxHeight - height) / 2).toFloat()
        }
    }

    /**
     * 更新位置显示
     */
    private fun updatePositionDisplay(x: Int, y: Int) {
        binding.positionXValue.text = x.toString()
        binding.positionYValue.text = y.toString()
    }

    /**
     * 更新大小显示
     */
    private fun updateSizeDisplay(width: Int, height: Int) {
        binding.sizeValue.text = width.toString()
    }

    /**
     * 更新透明度显示
     */
    private fun updateAlphaDisplay(alpha: Int) {
        binding.alphaValue.text = "${alpha}%"
    }

    /**
     * 更新切换按钮状态
     */
    private fun updateToggleButton() {
        val isShowing = videoPresentation?.isShowing == true
        binding.toggleWindowButton.text = if (isShowing) "隐藏悬浮窗" else "显示悬浮窗"
    }

    /**
     * 更新静音按钮状态
     */
    private fun updateMuteButton() {
        if (isMuted) {
            // 静音状态
            binding.muteButton.setIconResource(android.R.drawable.ic_lock_silent_mode)
            binding.muteButton.contentDescription = "取消静音"
        } else {
            // 非静音状态
            binding.muteButton.setIconResource(android.R.drawable.ic_lock_silent_mode_off)
            binding.muteButton.contentDescription = "静音"
        }
    }

    /**
     * Activity恢复到前台
     */
    override fun onResume() {
        super.onResume()
        // 重新显示Presentation（如果存在）
        if (videoPresentation != null && !videoPresentation?.isShowing!!) {
            val display = displayManager.getDisplay(drivingDisplayId)
                ?: displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
            display?.let {
                try {
                    videoPresentation?.show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "重新显示Presentation失败", e)
                }
            }
        }
    }

    /**
     * Activity进入后台
     */
    override fun onPause() {
        super.onPause()
        // 不关闭Presentation，让它在驾驶屏继续显示
    }

    /**
     * 初始化音频输出控制器
     */
    private fun initializeAudioOutputController() {
        // 初始化WebSocket服务器（在后台线程启动）
        webSocketServer = com.example.floatingscreencasting.websocket.CarWebSocketServer(8080)
        Thread {
            try {
                webSocketServer.start()
                Log.i("MainActivity", "WebSocket服务器已启动，监听端口8080")
            } catch (e: Exception) {
                Log.e("MainActivity", "WebSocket服务器启动失败", e)
            }
        }.start()

        // 初始化DLNA DMC客户端（用于发现和控制手机设备）
        dlnaDmcClient = DlnaControlPoint(this)

        // 初始化手机设备管理器
        phoneDeviceManager = PhoneDeviceManager(this)

        // 初始化音频输出控制器（传入WebSocket服务器）
        audioOutputController = AudioOutputController(dlnaDmcClient, phoneDeviceManager, webSocketServer)

        // 设置静音控制回调
        audioOutputController.setMuteControlCallback(object : AudioOutputController.MuteControlCallback {
            override fun setMuted(muted: Boolean) {
                // 通知VideoPresentation静音/取消静音
                videoPresentation?.setMuted(muted)
                Log.d("MainActivity", "音频输出控制器请求静音: $muted")
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
                return videoPresentation?.getExoPlayer()?.currentPosition ?: 0L
            }
        })

        // 启动DLNA DMC客户端（开始发现手机设备）
        dlnaDmcClient.start()

        // 监听WebSocket连接状态
        webSocketServer.onClientConnected = { clientId ->
            Log.i("MainActivity", "手机端已连接: $clientId")
            runOnUiThread {
                Toast.makeText(this, "FSCast Remote已连接", Toast.LENGTH_SHORT).show()
            }
        }

        webSocketServer.onClientDisconnected = { clientId ->
            Log.i("MainActivity", "手机端已断开: $clientId")
            runOnUiThread {
                Toast.makeText(this, "FSCast Remote已断开", Toast.LENGTH_SHORT).show()
            }
        }

        // 监听设备列表变化
        dlnaDmcClient.onDeviceListChanged = { devices ->
            phoneDeviceManager.updateDevices(devices)
            Log.d("MainActivity", "发现 ${devices.size} 个DLNA设备")
        }

        Log.d("MainActivity", "音频输出控制器初始化完成")
    }

    /**
     * 初始化DLNA服务
     */
    private fun initializeDlnaService() {
        dlnaService = DlnaRendererService.getInstance(this)

        dlnaService.apply {
            onCastingStateChanged = { isCasting, title ->
                updateCastingStatus(isCasting, title)
            }

            onErrorOccurred = { error ->
                Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
            }

            onPlayMedia = { uri, httpHeaders ->
                // 保存当前视频URI和HTTP头到音频输出控制器
                audioOutputController.setCurrentVideoUri(uri, httpHeaders)

                // 播放视频
                videoPresentation?.playMedia(uri)
                if (uri.isNotEmpty()) {
                    dlnaService.updateTransportState("PLAYING")
                }

                Log.d("MainActivity", "DLNA投屏: URI长度=${uri.length}, HTTP头数量=${httpHeaders.size}")
            }

            onStopMedia = {
                videoPresentation?.stop()
            }

            onPauseMedia = {
                videoPresentation?.pause()
            }

            onSeekMedia = { target ->
                // 解析Seek目标
                try {
                    val seconds = if (target.contains(":")) {
                        val parts = target.split(":")
                        parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                    } else {
                        target.toFloat().toInt()
                    }
                    videoPresentation?.seekTo(seconds * 1000L)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Seek失败", e)
                }
            }

            onGetDuration = {
                // 获取视频总时长（毫秒转换为秒）
                videoPresentation?.getExoPlayer()?.duration?.div(1000L) ?: 0L
            }

            onGetPosition = {
                // 获取当前播放位置（毫秒转换为秒）
                videoPresentation?.getExoPlayer()?.currentPosition?.div(1000L) ?: 0L
            }
        }

        lifecycleScope.launch {
            val success = dlnaService.start()
            if (success) {
                Log.d("MainActivity", "DLNA服务启动成功")
            } else {
                Log.e("MainActivity", "DLNA服务启动失败")
            }
        }
    }

    /**
     * 更新投屏状态显示
     */
    private fun updateCastingStatus(isCasting: Boolean, title: String? = null) {
        binding.castingStatusText.text = if (isCasting) {
            title ?: "投屏中"
        } else {
            "等待投屏..."
        }

        binding.statusIndicator.setCardBackgroundColor(
            getColor(
                if (isCasting) android.R.color.holo_green_dark
                else android.R.color.darker_gray
            )
        )
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)

        try {
            unregisterReceiver(playbackErrorReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "取消注册广播接收器失败", e)
        }

        // 注销EventBus
        try {
            EventBus.getDefault().unregister(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "取消注册EventBus失败", e)
        }

        // 停止WebSocket服务器
        try {
            if (::webSocketServer.isInitialized) {
                webSocketServer.stop()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "停止WebSocket服务器失败", e)
        }

        lifecycleScope.launch {
            dlnaService.stop()
        }

        // 停止并释放音频输出控制器
        audioOutputController.release()
        dlnaDmcClient.stop()

        displayManager.unregisterDisplayListener(displayListener)
        videoPresentation?.dismiss()
        _binding = null
        super.onDestroy()
    }

    /**
     * EventBus订阅方法 - 接收静音状态变化
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMuteEvent(event: MuteEvent) {
        isMuted = event.isMuted
        updateMuteButton()
    }
}
