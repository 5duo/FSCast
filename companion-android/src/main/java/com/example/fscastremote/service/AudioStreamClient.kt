package com.example.fscastremote.service

import android.util.Log
import com.example.fscastremote.common.Constants
import com.example.fscastremote.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

private const val TAG = "AudioStreamClient"

/**
 * TCP 音频流客户端
 */
class AudioStreamClient(
    private val audioPlayer: AudioTrackPlayer
) {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val recvBuffer = ByteArrayOutputStream()
    private var heartbeatJob: Job? = null
    private var receiveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    var onCommandResult: ((CommandResult) -> Unit)? = null
    var onOutputChanged: ((String) -> Unit)? = null
    var onFormatHeader: ((PcmAudioFormat) -> Unit)? = null

    private var serverIp = ""

    suspend fun connect(ip: String, port: Int = Constants.TCP_PORT): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                serverIp = ip

                socket = Socket(ip, port).apply {
                    soTimeout = 0
                }
                inputStream = socket?.getInputStream()
                outputStream = socket?.getOutputStream()

                _isConnected.value = true
                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "Connected to $ip:$port")

                startReceiveLoop()
                startHeartbeat()

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed: ${e.message}")
                handleDisconnect()
                Result.failure(e)
            }
        }
    }

    fun disconnect() {
        stopHeartbeat()
        receiveJob?.cancel()
        receiveJob = null

        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Close socket error: ${e.message}")
        }

        socket = null
        inputStream = null
        outputStream = null
        recvBuffer.reset()

        _isConnected.value = false
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "Disconnected")
    }

    private fun startReceiveLoop() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            val buffer = ByteArray(8192)
            while (isActive && _isConnected.value) {
                try {
                    val n = inputStream?.read(buffer) ?: -1
                    if (n > 0) {
                        handleMessage(buffer.copyOf(n))
                    } else if (n < 0) {
                        Log.i(TAG, "Connection closed by server")
                        break
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Receive error: ${e.message}")
                        break
                    }
                }
            }
            handleDisconnect()
        }
    }

    private fun handleMessage(data: ByteArray) {
        recvBuffer.write(data)
        parseFrames()
    }

    private fun parseFrames() {
        val buffer = recvBuffer.toByteArray()
        var offset = 0

        while (offset < buffer.size) {
            if (offset >= buffer.size) break

            val frameType = buffer[offset].toInt() and 0xFF
            var consumed = 0

            when (frameType) {
                Constants.FRAME_TYPE_HEARTBEAT.toInt() -> {
                    consumed = 1
                }
                Constants.FRAME_TYPE_FORMAT_HEADER.toInt(),
                Constants.FRAME_TYPE_FORMAT_CHANGE.toInt() -> {
                    if (buffer.size - offset < 13) break
                    val sampleRate = bytesToInt(buffer, offset + 1)
                    val channels = bytesToInt(buffer, offset + 5)
                    val encoding = bytesToInt(buffer, offset + 9)
                    consumed = 13

                    val format = PcmAudioFormat(sampleRate, channels, encoding)
                    Log.i(TAG, "Format: ${format.sampleRate}Hz, ${format.channels}ch")

                    if (frameType == Constants.FRAME_TYPE_FORMAT_HEADER.toInt()) {
                        onFormatHeader?.invoke(format)
                    }
                    audioPlayer.configure(format)
                }
                Constants.FRAME_TYPE_PCM_DATA.toInt() -> {
                    if (buffer.size - offset < 5) break
                    val dataLength = bytesToInt(buffer, offset + 1)
                    if (buffer.size - offset < 5 + dataLength) break

                    val pcmData = buffer.sliceArray(offset + 5 until offset + 5 + dataLength)
                    consumed = 5 + dataLength
                    audioPlayer.writePcmData(pcmData)
                }
                Constants.FRAME_TYPE_STATE_SYNC.toInt() -> {
                    if (buffer.size - offset < 5) break
                    val jsonLen = bytesToInt(buffer, offset + 1)
                    if (buffer.size - offset < 5 + jsonLen) break

                    val jsonStr = String(buffer, offset + 5, jsonLen)
                    consumed = 5 + jsonLen
                    parseStateSync(jsonStr)
                }
                Constants.FRAME_TYPE_COMMAND_RESULT.toInt() -> {
                    if (buffer.size - offset < 5) break
                    val cmdJsonLen = bytesToInt(buffer, offset + 1)
                    if (buffer.size - offset < 5 + cmdJsonLen) break

                    val cmdJsonStr = String(buffer, offset + 5, cmdJsonLen)
                    consumed = 5 + cmdJsonLen
                    parseCommandResult(cmdJsonStr)
                }
                Constants.FRAME_TYPE_OUTPUT_CHANGED.toInt() -> {
                    if (buffer.size - offset < 5) break
                    val outJsonLen = bytesToInt(buffer, offset + 1)
                    if (buffer.size - offset < 5 + outJsonLen) break

                    val outJsonStr = String(buffer, offset + 5, outJsonLen)
                    consumed = 5 + outJsonLen
                    parseOutputChanged(outJsonStr)
                }
                else -> {
                    Log.w(TAG, "Unknown frame type: 0x${frameType.toString(16)}")
                    consumed = 1
                }
            }

            if (consumed <= 0) break
            offset += consumed
        }

        if (offset > 0) {
            val remaining = buffer.drop(offset).toByteArray()
            recvBuffer.reset()
            recvBuffer.write(remaining)
        }
    }

    private fun bytesToInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun parseStateSync(json: String) {
        try {
            val state = PlaybackState(
                isPlaying = extractJsonBoolean(json, "is_playing") ?: false,
                positionMs = extractJsonLong(json, "position_ms") ?: 0,
                durationMs = extractJsonLong(json, "duration_ms") ?: 0,
                title = extractJsonString(json, "title") ?: "",
                bufferedPositionMs = extractJsonLong(json, "buffered_position_ms") ?: 0,
                audioOutput = extractJsonString(json, "audio_output") ?: "speaker",
                selectedDevice = extractJsonString(json, "selected_device") ?: ""
            )
            _playbackState.value = state
        } catch (e: Exception) {
            Log.e(TAG, "Parse state sync failed: ${e.message}")
        }
    }

    private fun parseCommandResult(json: String) {
        try {
            val result = CommandResult(
                action = extractJsonString(json, "action") ?: "",
                success = extractJsonBoolean(json, "success") ?: false,
                positionMs = extractJsonLong(json, "position_ms") ?: 0,
                output = extractJsonString(json, "output") ?: "",
                error = extractJsonString(json, "error") ?: ""
            )
            onCommandResult?.invoke(result)
        } catch (e: Exception) {
            Log.e(TAG, "Parse command result failed: ${e.message}")
        }
    }

    private fun parseOutputChanged(json: String) {
        try {
            val output = extractJsonString(json, "output") ?: "speaker"
            Log.i(TAG, "Audio output changed: $output")
            onOutputChanged?.invoke(output)
        } catch (e: Exception) {
            Log.e(TAG, "Parse output changed failed: ${e.message}")
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        val match = regex.find(json) ?: return null
        return match.groupValues[1]
    }

    private fun extractJsonBoolean(json: String, key: String): Boolean? {
        val pattern = "\"$key\"\\s*:\\s*(true|false)"
        val regex = Regex(pattern)
        val match = regex.find(json) ?: return null
        return match.groupValues[1].toBoolean()
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val regex = Regex(pattern)
        val match = regex.find(json) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private suspend fun sendCommand(payload: Map<String, Any>): Result<Unit> {
        if (!_isConnected.value) {
            return Result.failure(Exception("Not connected"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val json = buildJson(payload)
                val jsonBytes = json.toByteArray()

                val frame = ByteArray(1 + 4 + jsonBytes.size)
                frame[0] = Constants.FRAME_TYPE_COMMAND

                frame[1] = (jsonBytes.size and 0xFF).toByte()
                frame[2] = ((jsonBytes.size shr 8) and 0xFF).toByte()
                frame[3] = ((jsonBytes.size shr 16) and 0xFF).toByte()
                frame[4] = ((jsonBytes.size shr 24) and 0xFF).toByte()

                System.arraycopy(jsonBytes, 0, frame, 5, jsonBytes.size)

                outputStream?.write(frame)
                outputStream?.flush()

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Send command failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    private fun buildJson(payload: Map<String, Any>): String {
        val sb = StringBuilder("{")
        payload.forEach { (key, value) ->
            if (sb.length > 1) sb.append(",")
            when (value) {
                is String -> sb.append("\"$key\":\"$value\"")
                is Number -> sb.append("\"$key\":$value")
                is Boolean -> sb.append("\"$key\":$value")
                else -> sb.append("\"$key\":\"$value\"")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    suspend fun sendHelloAck(deviceName: String): Result<Unit> {
        return sendCommand(mapOf(
            "type" to "hello_ack",
            "device_name" to deviceName,
            "platform" to Constants.PLATFORM,
            "version" to Constants.PROTOCOL_VERSION
        ))
    }

    suspend fun sendPlay(): Result<Unit> {
        return sendCommand(mapOf("type" to "command", "action" to "play"))
    }

    suspend fun sendPause(): Result<Unit> {
        return sendCommand(mapOf("type" to "command", "action" to "pause"))
    }

    suspend fun sendStop(): Result<Unit> {
        return sendCommand(mapOf("type" to "command", "action" to "stop"))
    }

    suspend fun sendSeek(positionMs: Long): Result<Unit> {
        return sendCommand(mapOf(
            "type" to "command",
            "action" to "seek",
            "position_ms" to positionMs
        ))
    }

    suspend fun sendSetAudioOutput(output: String): Result<Unit> {
        return sendCommand(mapOf(
            "type" to "command",
            "action" to "set_audio_output",
            "output" to output
        ))
    }

    suspend fun sendGetState(): Result<Unit> {
        return sendCommand(mapOf("type" to "command", "action" to "get_state"))
    }

    suspend fun sendDisconnect(): Result<Unit> {
        return sendCommand(mapOf("type" to "command", "action" to "disconnect"))
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive && _isConnected.value) {
                delay(Constants.HEARTBEAT_INTERVAL_MS)
                sendHeartbeat()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun sendHeartbeat() {
        if (!_isConnected.value) return

        withContext(Dispatchers.IO) {
            try {
                val frame = byteArrayOf(Constants.FRAME_TYPE_HEARTBEAT)
                outputStream?.write(frame)
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Send heartbeat failed: ${e.message}")
                handleDisconnect()
            }
        }
    }

    private fun handleDisconnect() {
        if (!_isConnected.value) return
        _isConnected.value = false
        _connectionState.value = ConnectionState.DISCONNECTED
        stopHeartbeat()
        recvBuffer.reset()
        Log.i(TAG, "Disconnected")
    }

    fun getServerIp(): String = serverIp
}
