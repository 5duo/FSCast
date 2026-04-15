package com.example.floatingscreencasting.audio

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink

/**
 * 音频流传输总管理器。
 *
 * 协调以下组件的生命周期：
 * - AudioDiscoveryServer: UDP 设备发现
 * - AudioStreamServer: TCP 服务器（PCM 发送 + 控制命令接收 + 状态广播）
 * - PcmRingBuffer: PCM 环形缓冲区
 *
 * 通过 [createRenderersFactory] 创建自定义 RenderersFactory，
 * 在 ExoPlayer 内部注入 StreamingAudioSink 来拦截 PCM 数据。
 */
class AudioStreamManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioStreamManager"
        private const val PREFS_NAME = "audio_stream_prefs"
        private const val KEY_OUTPUT_MODE = "output_mode"
    }

    enum class AudioOutputMode { SPEAKER, PHONE }

    val pcmRingBuffer = PcmRingBuffer()

    private val audioStreamServer = AudioStreamServer(
        pcmRingBuffer = pcmRingBuffer,
        commandHandler = { clientId, action, params -> handleCommand(clientId, action, params) },
        stateProvider = { currentState }
    )

    private val audioDiscoveryServer = AudioDiscoveryServer()

    var outputMode: AudioOutputMode = AudioOutputMode.SPEAKER
        private set

    /** 手机端发来控制命令时回调（转发给 VideoPresentation 处理） */
    var onCommandReceived: ((action: String, params: Map<String, Any>) -> Unit)? = null

    /** 已连接设备列表变化回调（更新 UI） */
    var onClientListChanged: ((clients: List<AudioStreamServer.ConnectedClient>) -> Unit)? = null

    /** 音频输出模式变化回调（控制车机静音/恢复） */
    var onOutputModeChanged: ((muted: Boolean) -> Unit)? = null

    private var currentState = AudioStreamServer.PlaybackState()

    /**
     * 启动所有音频流服务（发现 + TCP）。
     */
    fun start() {
        audioDiscoveryServer.start()
        audioStreamServer.start()
        audioStreamServer.onClientListChanged = { clients ->
            onClientListChanged?.invoke(clients)
        }
        // 恢复保存的偏好
        outputMode = loadOutputPreference()
        Log.i(TAG, "AudioStreamManager 启动, 模式: $outputMode")
    }

    /**
     * 停止所有音频流服务。
     */
    fun stop() {
        audioDiscoveryServer.stop()
        audioStreamServer.stop()
        pcmRingBuffer.close()
        Log.i(TAG, "AudioStreamManager 已停止")
    }

    /**
     * 创建自定义 RenderersFactory，用于注入 StreamingAudioSink。
     *
     * 在 VideoPresentation 创建 ExoPlayer 时使用：
     * ```
     * val factory = audioStreamManager.createRenderersFactory()
     * ExoPlayer.Builder(context, factory).build()
     * ```
     *
     * 仅在 PHONE 模式下注入 StreamingAudioSink（拦截 PCM）。
     * SPEAKER 模式下返回标准 DefaultRenderersFactory（零开销）。
     */
    fun createRenderersFactory(): DefaultRenderersFactory {
        val ringBuffer = pcmRingBuffer
        val server = audioStreamServer

        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                // 调用父类创建默认的 DefaultAudioSink
                val defaultSink = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                    ?: throw IllegalStateException("无法创建 DefaultAudioSink")

                // 包装为 StreamingAudioSink，拦截 PCM 数据
                return StreamingAudioSink(
                    delegate = defaultSink,
                    pcmRingBuffer = ringBuffer,
                    onFormatChanged = { sampleRate, channelCount, encoding ->
                        Log.d(TAG, "音频格式: ${sampleRate}Hz, ${channelCount}ch, encoding=$encoding")
                        server.sendFormatHeader(sampleRate, channelCount, encoding)
                    }
                )
            }
        }
    }

    /**
     * 设置当前播放状态（供状态同步广播使用）。
     */
    fun setPlaybackState(state: AudioStreamServer.PlaybackState) {
        currentState = state
    }

    /**
     * 切换音频输出模式。
     */
    fun setOutputMode(mode: AudioOutputMode) {
        outputMode = mode
        saveOutputPreference(mode)
        when (mode) {
            AudioOutputMode.SPEAKER -> {
                // 取消选中手机设备
                audioStreamServer.selectClient(null)
                onOutputModeChanged?.invoke(false)
            }
            AudioOutputMode.PHONE -> {
                onOutputModeChanged?.invoke(true)
            }
        }
        Log.i(TAG, "音频输出模式切换: $mode")
    }

    /**
     * 选中指定手机设备接收音频。
     */
    fun selectClient(clientId: String) {
        outputMode = AudioOutputMode.PHONE
        saveOutputPreference(AudioOutputMode.PHONE)
        audioStreamServer.selectClient(clientId)
        onOutputModeChanged?.invoke(true)
        Log.i(TAG, "选中设备: $clientId")
    }

    /**
     * 断开指定手机设备。
     */
    fun disconnectClient(clientId: String) {
        audioStreamServer.disconnectClient(clientId)
    }

    /**
     * 是否有手机设备已连接。
     */
    fun isClientConnected(): Boolean = audioStreamServer.connectedClients.value.isNotEmpty()

    /**
     * 获取所有已连接的手机设备。
     */
    val connectedClients: List<AudioStreamServer.ConnectedClient>
        get() = audioStreamServer.connectedClients.value

    /**
     * 当前接收音频的设备 ID。
     */
    val selectedClientId: String?
        get() = audioStreamServer.selectedClientId

    /**
     * 服务是否在运行。
     */
    fun isRunning(): Boolean = audioStreamServer.isRunning()

    /**
     * 处理来自手机端的控制命令。
     */
    private fun handleCommand(clientId: String, action: String, params: Map<String, Any>) {
        Log.d(TAG, "收到命令: clientId=$clientId, action=$action, params=$params")
        when (action) {
            "set_audio_output" -> {
                val output = params["output"] as? String ?: "speaker"
                if (output == "phone") {
                    selectClient(clientId)
                } else {
                    setOutputMode(AudioOutputMode.SPEAKER)
                }
            }
            else -> {
                // 其他命令（play/pause/stop/seek）转发给 VideoPresentation
                onCommandReceived?.invoke(action, params)
            }
        }
    }

    private fun saveOutputPreference(mode: AudioOutputMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OUTPUT_MODE, mode.name)
            .apply()
    }

    private fun loadOutputPreference(): AudioOutputMode {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_OUTPUT_MODE, AudioOutputMode.SPEAKER.name)
        return try {
            AudioOutputMode.valueOf(name ?: "SPEAKER")
        } catch (e: Exception) {
            AudioOutputMode.SPEAKER
        }
    }
}
