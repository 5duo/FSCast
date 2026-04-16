package com.example.fscastremote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.fscastremote.MainActivity
import com.example.fscastremote.R
import com.example.fscastremote.model.ConnectionState
import com.example.fscastremote.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "AudioPlaybackService"

class AudioPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()

    private val audioPlayer = AudioTrackPlayer()
    private var audioStreamClient: AudioStreamClient? = null
    private var discoveryClient: DiscoveryClient? = null
    private var bluetoothMonitor: com.example.fscastremote.bluetooth.BluetoothMonitor? = null
    private var audioFocusManager: AudioFocusManager? = null

    companion object {
        const val CHANNEL_ID = "audio_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.fscastremote.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.fscastremote.ACTION_PAUSE"
        const val ACTION_DISCONNECT = "com.example.fscastremote.ACTION_DISCONNECT"
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        createNotificationChannel()

        audioStreamClient = AudioStreamClient(audioPlayer).apply {
            onCommandResult = { result ->
                handleCommandResult(result)
            }
            onOutputChanged = { output ->
                handleOutputChanged(output)
            }
            onFormatHeader = { format ->
                Log.i(TAG, "Format received: ${format.sampleRate}Hz, ${format.channels}ch")
            }
        }

        discoveryClient = DiscoveryClient()

        bluetoothMonitor = com.example.fscastremote.bluetooth.BluetoothMonitor(this).apply {
            onBluetoothDisconnected = {
                Log.i(TAG, "Bluetooth disconnected, auto pause")
                serviceScope.launch {
                    audioStreamClient?.sendPause()
                }
                audioPlayer.pause()
            }
        }

        audioFocusManager = AudioFocusManager(this)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                serviceScope.launch {
                    audioStreamClient?.sendPlay()
                    audioPlayer.play()
                }
            }
            ACTION_PAUSE -> {
                serviceScope.launch {
                    audioStreamClient?.sendPause()
                    audioPlayer.pause()
                }
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    audioStreamClient?.sendDisconnect()
                    disconnect()
                }
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")

        bluetoothMonitor?.stop()
        audioFocusManager?.abandonAudioFocus()
        audioPlayer.release()
        audioStreamClient?.disconnect()

        serviceScope.cancel()
    }

    fun getAudioStreamClient(): AudioStreamClient? = audioStreamClient

    fun getDiscoveryClient(): DiscoveryClient? = discoveryClient

    fun getPlaybackState(): StateFlow<PlaybackState>? = audioStreamClient?.playbackState

    fun getConnectionState(): StateFlow<ConnectionState>? = audioStreamClient?.connectionState

    fun getIsConnected(): Boolean = audioStreamClient?.isConnected?.value ?: false

    suspend fun connect(ip: String, port: Int) {
        audioStreamClient?.connect(ip, port)?.onSuccess {
            audioPlayer.play()
            bluetoothMonitor?.start()
            audioFocusManager?.requestAudioFocus()
            updateNotification()
        }
    }

    fun disconnect() {
        audioPlayer.pause()
        audioPlayer.release()
        bluetoothMonitor?.stop()
        audioFocusManager?.abandonAudioFocus()
        audioStreamClient?.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音频播放控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "控制音频播放的快捷操作"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val playIntent = createPendingIntent(ACTION_PLAY)
        val pauseIntent = createPendingIntent(ACTION_PAUSE)
        val disconnectIntent = createPendingIntent(ACTION_DISCONNECT)

        val playbackState = audioStreamClient?.playbackState?.value
        val isConnected = audioStreamClient?.isConnected?.value ?: false
        val isPlaying = playbackState?.isPlaying ?: false

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(playbackState?.title ?: "FSCast Remote")
            .setContentText(if (isConnected) "已连接" else "未连接")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "暂停" else "播放",
                if (isPlaying) pauseIntent else playIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "断开",
                disconnectIntent
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun handleCommandResult(result: com.example.fscastremote.model.CommandResult) {
        Log.i(TAG, "Command result: ${result.action}, success=${result.success}")
        if (!result.success) {
            Log.e(TAG, "Command failed: ${result.error}")
        }
        updateNotification()
    }

    private fun handleOutputChanged(output: String) {
        Log.i(TAG, "Audio output changed: $output")
        if (output == "speaker") {
            audioPlayer.pause()
        } else {
            audioPlayer.play()
        }
        updateNotification()
    }
}

private class AudioFocusManager(private val context: Context) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hasFocus = false
    private var focusRequest: AudioFocusRequest? = null

    fun requestAudioFocus(): Boolean {
        if (hasFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()

            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    fun abandonAudioFocus() {
        if (!hasFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }

        hasFocus = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 永久失去焦点
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 临时失去焦点
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 可以降低音量
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 重新获得焦点
            }
        }
    }
}
