package com.example.floatingscreencasting.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.floatingscreencasting.R
import com.example.floatingscreencasting.data.remote.dlna.DlnaRendererService
import com.example.floatingscreencasting.presentation.VideoPresentation
import com.example.floatingscreencasting.websocket.CarWebSocketServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 投屏前台服务
 *
 * 功能：
 * 1. 保持DLNA服务在后台运行
 * 2. 保持WebSocket服务器在后台运行
 * 3. 保持Presentation在后台显示
 * 4. 显示持续通知防止服务被杀死
 */
class CastingForegroundService : Service() {

    companion object {
        private const val TAG = "CastingForegroundService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "casting_service_channel"

        const val ACTION_START = "com.example.floatingscreencasting.START_CASTING"
        const val ACTION_STOP = "com.example.floatingscreencasting.STOP_CASTING"
        const val ACTION_UPDATE_STATUS = "com.example.floatingscreencasting.UPDATE_STATUS"

        // 状态Extra
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_VIDEO_TITLE = "video_title"

        /**
         * 启动前台服务
         */
        fun start(context: Context) {
            val intent = Intent(context, CastingForegroundService::class.java)
            intent.action = ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "前台服务启动请求已发送")
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, CastingForegroundService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
            Log.i(TAG, "服务停止请求已发送")
        }

        /**
         * 更新播放状态
         */
        fun updateStatus(context: Context, isPlaying: Boolean, title: String = "") {
            val intent = Intent(context, CastingForegroundService::class.java)
            intent.action = ACTION_UPDATE_STATUS
            intent.putExtra(EXTRA_IS_PLAYING, isPlaying)
            intent.putExtra(EXTRA_VIDEO_TITLE, title)
            context.startService(intent)
        }
    }

    // 服务范围
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 服务组件
    private var dlnaService: DlnaRendererService? = null
    private var webSocketServer: CarWebSocketServer? = null
    private var presentation: VideoPresentation? = null

    // 当前状态
    private var isPlaying = false
    private var currentVideoTitle = ""

    // Binder
    inner class LocalBinder : Binder() {
        fun getService(): CastingForegroundService = this@CastingForegroundService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "========== CastingForegroundService onCreate ==========")

        // 创建通知渠道
        createNotificationChannel()

        // 启动前台服务并显示通知
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        Log.i(TAG, "前台服务已启动，通知已显示")

        // 初始化服务组件
        initializeServices()

        // 启动Presentation保活任务
        startPresentationKeepAlive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "收到START命令")
                // 服务已在onCreate中初始化
            }
            ACTION_STOP -> {
                Log.i(TAG, "收到STOP命令")
                cleanupAndStop()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATUS -> {
                val newIsPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val newTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
                updatePlaybackStatus(newIsPlaying, newTitle)
            }
        }

        // START_STICKY：服务被杀死后自动重启
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind - 客户端绑定到服务")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind - 客户端解绑")
        return true // 允许重新绑定
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "========== CastingForegroundService onDestroy ==========")

        // 停止协程
        serviceScope.cancel()

        // 清理服务组件
        cleanupServices()

        Log.i(TAG, "前台服务已销毁")
    }

    /**
     * 创建通知渠道（Android 8.0+需要）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "投屏服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持投屏服务在后台运行，可接收投屏请求"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "通知渠道已创建: $CHANNEL_ID")
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val contentText = if (isPlaying) {
            if (currentVideoTitle.isNotEmpty()) {
                "正在投屏: $currentVideoTitle"
            } else {
                "投屏服务运行中"
            }
        } else {
            "服务运行中，等待投屏..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FSCast投屏服务")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true) // 不可滑动删除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * 更新通知
     */
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 初始化服务组件
     */
    private fun initializeServices() {
        Log.i(TAG, "初始化服务组件")

        serviceScope.launch(Dispatchers.IO) {
            try {
                // 初始化DLNA服务
                if (dlnaService == null) {
                    Log.d(TAG, "创建DLNA服务实例")
                    dlnaService = DlnaRendererService.getInstance(applicationContext)

                    val started = dlnaService?.start() ?: false
                    Log.i(TAG, "DLNA服务启动${if (started) "成功" else "失败"}")
                }

                // 初始化WebSocket服务器
                if (webSocketServer == null) {
                    Log.d(TAG, "创建WebSocket服务器")
                    webSocketServer = CarWebSocketServer(9999)

                    webSocketServer?.start()
                    Log.i(TAG, "WebSocket服务器已启动，端口: 9999")
                }

            } catch (e: Exception) {
                Log.e(TAG, "初始化服务组件失败", e)
            }
        }
    }

    /**
     * 清理服务组件
     */
    private fun cleanupServices() {
        Log.i(TAG, "清理服务组件")

        try {
            // 停止WebSocket服务器
            webSocketServer?.stop()
            webSocketServer = null
            Log.i(TAG, "WebSocket服务器已停止")

            // 停止DLNA服务
            dlnaService?.stop()
            dlnaService = null
            Log.i(TAG, "DLNA服务已停止")

            // 隐藏Presentation
            presentation?.dismiss()
            presentation = null
            Log.i(TAG, "Presentation已隐藏")

        } catch (e: Exception) {
            Log.e(TAG, "清理服务组件失败", e)
        }
    }

    /**
     * 启动Presentation保活任务
     * 定期检查Presentation状态，确保它在后台也能显示
     */
    private fun startPresentationKeepAlive() {
        serviceScope.launch(Dispatchers.Main) {
            while (isActive) {
                try {
                    // 检查Presentation状态
                    if (presentation != null) {
                        if (!presentation!!.isShowing) {
                            Log.w(TAG, "Presentation未显示，尝试重新显示")
                            presentation?.show()
                        }
                    }

                    // 每秒检查一次
                    delay(1000)

                } catch (e: Exception) {
                    Log.e(TAG, "Presentation保活任务出错", e)
                    delay(5000) // 出错后等待5秒再试
                }
            }
        }
    }

    /**
     * 设置Presentation
     */
    fun setPresentation(presentation: VideoPresentation?) {
        Log.d(TAG, "设置Presentation: $presentation")
        this.presentation = presentation
    }

    /**
     * 获取Presentation
     */
    fun getPresentation(): VideoPresentation? = presentation

    /**
     * 获取DLNA服务
     */
    fun getDlnaService(): DlnaRendererService? = dlnaService

    /**
     * 获取WebSocket服务器
     */
    fun getWebSocketServer(): CarWebSocketServer? = webSocketServer

    /**
     * 更新播放状态
     */
    private fun updatePlaybackStatus(playing: Boolean, title: String) {
        isPlaying = playing
        currentVideoTitle = title

        // 更新通知
        updateNotification()

        Log.d(TAG, "播放状态已更新: playing=$playing, title=$title")
    }

    /**
     * 清理并停止服务
     */
    private fun cleanupAndStop() {
        Log.i(TAG, "清理并停止服务")

        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        // 清理资源
        cleanupServices()
    }

    /**
     * 获取应用上下文
     */
    private fun applicationContext(): Context = applicationContext
}
