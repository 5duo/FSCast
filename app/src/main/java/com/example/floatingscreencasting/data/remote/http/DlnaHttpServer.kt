package com.example.floatingscreencasting.data.remote.http

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.InetAddress
import javax.xml.parsers.DocumentBuilderFactory

/**
 * DLNA投屏视频元数据
 */
data class DlnaMediaMetadata(
    val uri: String,
    val title: String = "",
    val durationMs: Long = 0,
    val httpHeaders: Map<String, String> = emptyMap()
)

/**
 * DLNA HTTP服务器
 * 负责提供设备描述文件和接收控制命令
 */
class DlnaHttpServer : NanoHTTPD("0.0.0.0", 49152) {

    companion object {
        private const val TAG = "DlnaHttpServer"
        // 固定设备UUID（伪装成小米电视）
        private const val DEVICE_UUID = "583f8100-1de2-11db-8981-000c298458a8"
    }

    // 播放状态跟踪
    private var transportState: String = "STOPPED"
        set(value) {
            field = value
            Log.d(TAG, "TransportState变更: $value")
        }

    /**
     * 更新传输状态
     */
    fun updateTransportState(state: String) {
        transportState = when (state) {
            "PLAYING", "playing" -> "PLAYING"
            "PAUSED", "paused" -> "PAUSED_PLAYBACK"
            "STOPPED", "stopped" -> "STOPPED"
            else -> state
        }
    }

    override fun start() {
        try {
            Log.d(TAG, "========== 正在启动HTTP服务器... ==========")
            Log.d(TAG, "调用super.start()之前...")
            super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "super.start()调用完成")
            Log.d(TAG, "HTTP服务器已启动，监听所有接口")
            Log.d(TAG, "服务器端口: ${this.listeningPort}")
        } catch (e: Exception) {
            Log.e(TAG, "启动HTTP服务器失败", e)
            throw e
        }
    }

    private var onPlayCommand: ((DlnaMediaMetadata) -> Unit)? = null
    private var onStopCommand: (() -> Unit)? = null
    private var onPauseCommand: (() -> Unit)? = null
    private var onSeekCommand: ((String) -> Unit)? = null

    // 获取播放状态的回调
    private var onGetDuration: (() -> Long)? = null
    private var onGetPosition: (() -> Long)? = null

    // 保存最后一次的HTTP头
    private var lastHttpHeaders: Map<String, String> = emptyMap()

    /**
     * 设置播放命令回调
     */
    fun setPlayCommand(callback: (DlnaMediaMetadata) -> Unit) {
        onPlayCommand = callback
    }

    /**
     * 设置停止命令回调
     */
    fun setStopCommand(callback: () -> Unit) {
        onStopCommand = callback
    }

    /**
     * 设置暂停命令回调
     */
    fun setPauseCommand(callback: () -> Unit) {
        onPauseCommand = callback
    }

    /**
     * 设置Seek命令回调
     */
    fun setSeekCommand(callback: (String) -> Unit) {
        onSeekCommand = callback
    }

    /**
     * 设置获取时长回调
     */
    fun setGetDurationCallback(callback: () -> Long) {
        onGetDuration = callback
    }

    /**
     * 设置获取位置回调
     */
    fun setGetPositionCallback(callback: () -> Long) {
        onGetPosition = callback
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "收到请求: $method $uri")

        return when {
            // 设备描述文件
            uri == "/dlna/description.xml" -> {
                Log.d(TAG, "返回设备描述文件")
                newFixedLengthResponse(Response.Status.OK, "text/xml", getDeviceDescription())
            }

            // SCPD服务描述文件
            uri.endsWith(".xml") -> {
                Log.d(TAG, "返回SCPD文件: $uri")
                newFixedLengthResponse(Response.Status.OK, "text/xml", getServiceDescription())
            }

            // AVTransport 控制
            uri.contains("/control") -> {
                handleControlCommand(session)
            }

            // 连接管理
            uri.contains("/connection") -> {
                handleConnectionCommand(session)
            }

            // 其他请求返回404
            else -> {
                Log.d(TAG, "未知的请求: $uri")
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        }
    }

    /**
     * 处理控制命令
     */
    private fun handleControlCommand(session: IHTTPSession): Response {
        try {
            val headers = session.headers
            val soapAction = headers["soapaction"] ?: headers["SOAPAction"]

            // 保存HTTP头信息（提取反爬虫相关的头）
            lastHttpHeaders = extractAntiCrawlerHeaders(headers)

            Log.d(TAG, "========== 收到控制请求 ==========")
            Log.d(TAG, "SOAPAction: $soapAction")
            Log.d(TAG, "所有Headers: ${headers.keys}")
            headers.forEach { (key, value) ->
                Log.d(TAG, "  $key = $value")
            }

            // 使用NanoHTTPD的方法获取请求体
            val bodyMap: MutableMap<String, String> = HashMap()
            session.parseBody(bodyMap)
            val bodyStr = bodyMap["postData"] ?: ""

            Log.d(TAG, "请求体长度: ${bodyStr.length}")
            if (bodyStr.isNotEmpty()) {
                Log.d(TAG, "请求体前500字符: ${bodyStr.take(500)}")
            }
            Log.d(TAG, "================================")

            // 解析SOAP命令并获取响应
            val soapResponse = parseSoapCommand(soapAction, bodyStr)

            return newFixedLengthResponse(
                Response.Status.OK,
                "text/xml; charset=\"utf-8\"",
                soapResponse
            ).apply {
                addHeader("EXT", "")
                addHeader("Server", "FloatingScreenCasting/1.0 UPnP/1.0 DLNADOC/1.50")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理控制命令失败", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Error: ${e.message}"
            )
        }
    }

    /**
     * 解析SOAP命令并返回SOAP响应
     */
    private fun parseSoapCommand(soapAction: String?, body: String): String {
        return when {
            soapAction?.contains("#SetAVTransportURI") == true -> {
                // 提取视频URL
                var uri = extractValue(body, "CurrentURI")
                val metadata = extractValue(body, "CurrentURIMetaData")

                // **关键：处理XML实体转义（90%开发者会踩的坑）**
                // DLNA传输的XML中，URL里的&符号会被转义为&amp;
                // 必须在传给播放器之前进行反转义处理
                uri = uri.replace("&amp;", "&")
                uri = uri.replace("&lt;", "<")
                uri = uri.replace("&gt;", ">")
                uri = uri.replace("&quot;", "\"")

                Log.d(TAG, "========== DLNA投屏请求 ==========")
                Log.d(TAG, "原始URI: ${extractValue(body, "CurrentURI").take(100)}...")
                Log.d(TAG, "反转义后URI: $uri")
                Log.d(TAG, "URI长度: ${uri.length}")
                Log.d(TAG, "Metadata: ${metadata.take(500)}")

                // 解析metadata获取标题和时长
                val (title, durationMs, metadataHeaders) = parseMetadata(metadata)

                // 合并HTTP头（metadata中的HTTP头优先级更高）
                val mergedHeaders = mutableMapOf<String, String>()
                mergedHeaders.putAll(lastHttpHeaders)
                mergedHeaders.putAll(metadataHeaders)

                // 保存合并后的HTTP头
                lastHttpHeaders = mergedHeaders

                // 检测URL类型
                val urlType = when {
                    uri.contains("bilibili.com/video") || uri.contains("b23.tv") -> {
                        "Bilibili网页URL (需要解析BVID)"
                    }
                    uri.contains("bilivideo.com") -> {
                        "Bilibili视频流URL (可能需要处理设备参数)"
                    }
                    uri.contains("bilibili") && uri.contains("m3u8") -> {
                        "Bilibili m3u8流 (可能已签名)"
                    }
                    else -> "其他URL"
                }
                Log.d(TAG, "URL类型识别: $urlType")

                // 检测是否是Bilibili视频
                if (uri.contains("bilibili") || uri.contains("bili") ||
                    uri.contains("acgvideo") || uri.contains("cnbon")) {
                    Log.e(TAG, "⚠️ 检测到Bilibili视频URL")
                    Log.e(TAG, "这可能需要特殊的反爬虫处理")
                }

                // 创建元数据对象
                val mediaMetadata = DlnaMediaMetadata(
                    uri = uri,
                    title = title,
                    durationMs = durationMs,
                    httpHeaders = mergedHeaders
                )

                Log.d(TAG, "解析结果 - 标题: $title, 时长: ${durationMs}ms (${formatTime(durationMs / 1000)})")

                // 异步处理播放命令
                CoroutineScope(Dispatchers.Main).launch {
                    onPlayCommand?.invoke(mediaMetadata)
                }

                // 返回成功响应
                """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                           s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:SetAVTransportURIResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/>
                    </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction?.contains("#Play") == true -> {
                // 播放命令（对于已设置的URI）
                Log.d(TAG, "收到Play命令")
                transportState = "PLAYING"

                // 创建空的元数据对象（恢复播放）
                val emptyMetadata = DlnaMediaMetadata(
                    uri = "",
                    title = "",
                    durationMs = 0,
                    httpHeaders = lastHttpHeaders
                )

                CoroutineScope(Dispatchers.Main).launch {
                    onPlayCommand?.invoke(emptyMetadata)
                }

                """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                           s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:PlayResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/>
                    </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction?.contains("#Pause") == true -> {
                Log.d(TAG, "收到Pause命令")
                transportState = "PAUSED_PLAYBACK"

                CoroutineScope(Dispatchers.Main).launch {
                    onPauseCommand?.invoke()
                }

                """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                           s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:PauseResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/>
                    </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction?.contains("#Stop") == true -> {
                Log.d(TAG, "========== 收到Stop命令 ==========")
                Log.d(TAG, "SOAPAction: $soapAction")
                Log.d(TAG, "请求体: $body")
                transportState = "STOPPED"

                CoroutineScope(Dispatchers.Main).launch {
                    Log.d(TAG, "执行Stop命令回调")
                    onStopCommand?.invoke()
                    Log.d(TAG, "Stop命令回调执行完成")
                }

                Log.d(TAG, "========== Stop命令处理完成 ==========")
                """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                           s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:StopResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/>
                    </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction?.contains("#GetTransportInfo") == true -> {
                Log.d(TAG, "收到GetTransportInfo命令，当前状态: $transportState")

                """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                           s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <CurrentTransportState>$transportState</CurrentTransportState>
                            <CurrentTransportStatus>OK</CurrentTransportStatus>
                            <CurrentSpeed>1</CurrentSpeed>
                        </u:GetTransportInfoResponse>
                    </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction?.contains("#Seek") == true -> {
                val unit = extractValue(body, "Unit")
                val target = extractValue(body, "Target")
                Log.d(TAG, "收到Seek命令: unit=$unit, target=$target")
                Log.d(TAG, "Seek请求体: $body")

                // 验证target格式
                if (target.contains(":")) {
                    // HH:MM:SS格式
                    Log.d(TAG, "Seek目标格式: HH:MM:SS")
                } else {
                    // 相对时间格式
                    Log.d(TAG, "Seek目标格式: 相对时间或绝对时间")
                }

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        onSeekCommand?.invoke(target)
                        Log.d(TAG, "Seek命令处理完成")
                    } catch (e: Exception) {
                        Log.e(TAG, "Seek命令处理失败", e)
                    }
                }

                """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                           s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:SeekResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/>
                    </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction?.contains("#GetPositionInfo") == true -> {
                Log.d(TAG, "收到GetPositionInfo命令")

                // 获取当前播放位置（秒）
                val positionSeconds = onGetPosition?.invoke() ?: 0L
                val positionMs = positionSeconds * 1000

                // 获取总时长（秒）
                val durationSeconds = onGetDuration?.invoke() ?: 0L

                Log.d(TAG, "GetPositionInfo响应: position=${positionSeconds}s, duration=${durationSeconds}s")
                Log.d(TAG, "进度百分比: ${if (durationSeconds > 0) (positionSeconds * 100 / durationSeconds) else 0}%")

                // 格式化时间为HH:MM:SS格式
                // 对于无效时长，使用"00:00:00"
                val relTime = if (positionSeconds >= 0) formatTime(positionSeconds) else "00:00:00"
                val absTime = relTime
                val trackDuration = if (durationSeconds > 0) formatTime(durationSeconds) else "00:00:00"

                // 如果没有内容，返回特殊标记
                val trackMetaData = if (durationSeconds <= 0) "NOT_IMPLEMENTED" else "NOT_IMPLEMENTED"

                val responseXml = """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                           s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <TrackDuration>$trackDuration</TrackDuration>
                            <TrackMetaData>$trackMetaData</TrackMetaData>
                            <TrackURI></TrackURI>
                            <RelTime>$relTime</RelTime>
                            <AbsTime>$absTime</AbsTime>
                            <RelCount>2147483647</RelCount>
                            <AbsCount>2147483647</AbsCount>
                        </u:GetPositionInfoResponse>
                    </s:Body>
                </s:Envelope>
                """.trimIndent()

                Log.d(TAG, "========== GetPositionInfo XML响应 ==========")
                Log.d(TAG, "TrackDuration: $trackDuration")
                Log.d(TAG, "RelTime: $relTime")
                Log.d(TAG, "AbsTime: $absTime")
                Log.d(TAG, "完整响应:\n$responseXml")
                Log.d(TAG, "=========================================")

                responseXml
            }

            else -> {
                Log.w(TAG, "未知的SOAP命令: $soapAction")
                """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                           s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                    </s:Body>
                </s:Envelope>
                """.trimIndent()
            }
        }
    }

    /**
     * 格式化时间为HH:MM:SS格式
     * @param seconds 总秒数
     * @return 格式化的时间字符串
     */
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    /**
     * 从XML中提取标签值
     */
    private fun extractValue(xml: String, tagName: String): String {
        val pattern = "<(?:ns0:)?$tagName[^>]*>(.*?)</(?:ns0:)?$tagName>".toRegex()
        val match = pattern.find(xml)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    /**
     * 从DLNA metadata中提取视频信息
     * @param metadataXml DIDL-Lite XML格式的metadata
     * @return Triple(标题, 时长毫秒, HTTP头)
     */
    private fun parseMetadata(metadataXml: String): Triple<String, Long, Map<String, String>> {
        var title = ""
        var durationMs = 0L
        val httpHeaders = mutableMapOf<String, String>()

        if (metadataXml.isBlank()) {
            Log.w(TAG, "⚠️ Metadata为空，无法解析标题和时长")
            return Triple(title, durationMs, httpHeaders)
        }

        try {
            Log.d(TAG, "========== 开始解析DLNA Metadata ==========")
            Log.d(TAG, "原始Metadata内容:\n$metadataXml")

            // **关键修复：HTML实体反转义**
            // B站等客户端发送的metadata可能被HTML转义（&lt; &gt; &amp; &quot;）
            // 必须先反转义才能正确解析XML
            val decodedMetadata = metadataXml
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")

            Log.d(TAG, "反转义后Metadata内容:\n$decodedMetadata")
            Log.d(TAG, "=========================================")

            // 提取标题 (dc:title)
            title = extractValue(decodedMetadata, "dc:title")
                .ifBlank { extractValue(decodedMetadata, "title") }

            // 提取时长 (res@duration 或 duration)
            val durationStr = extractValue(decodedMetadata, "res")
                .let { resValue ->
                    // 从res属性中提取duration
                    val durationPattern = """duration="([^"]+)"""".toRegex()
                    durationPattern.find(resValue)?.groupValues?.get(1)
                }
                ?: extractValue(decodedMetadata, "duration")

            if (durationStr.isNotBlank()) {
                durationMs = parseDurationToMs(durationStr)
            }

            // 提取HTTP头（从res属性中）
            val resElement = extractValue(decodedMetadata, "res")
            if (resElement.isNotBlank()) {
                // 提取protocolInfo
                val protocolInfoPattern = """protocolInfo="([^"]+)"""".toRegex()
                protocolInfoPattern.find(resElement)?.groupValues?.get(1)?.let { protocolInfo ->
                    // 解析protocolInfo: "http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
                    val parts = protocolInfo.split(":")
                    if (parts.size >= 4) {
                        httpHeaders["Content-Type"] = parts[2]
                    }
                }
            }

            Log.d(TAG, "Metadata解析结果:")
            Log.d(TAG, "  标题: $title")
            Log.d(TAG, "  时长: ${durationMs}ms (${formatTime(durationMs / 1000)})")
            Log.d(TAG, "  HTTP头: ${httpHeaders.keys}")

            // 如果没有获取到标题或时长，记录警告
            if (title.isBlank() || durationMs == 0L) {
                Log.w(TAG, "⚠️ Metadata中缺少标题或时长信息")
                Log.w(TAG, "  标题为空: ${title.isBlank()}")
                Log.w(TAG, "  时长为0: ${durationMs == 0L}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析metadata失败", e)
        }

        return Triple(title, durationMs, httpHeaders)
    }

    /**
     * 解析时长字符串为毫秒
     * 支持格式：
     * - HH:MM:SS (如 "1:23:45")
     * - MM:SS (如 "83:45")
     * - 纯秒数 (如 "5025")
     * - H:MM:SS.mmm (如 "1:23:45.123")
     */
    private fun parseDurationToMs(durationStr: String): Long {
        return try {
            when {
                durationStr.contains(":") && durationStr.split(":").size == 3 -> {
                    // HH:MM:SS 或 HH:MM:SS.mmm
                    val parts = durationStr.split(":")
                    val hours = parts[0].toFloat()
                    val minutes = parts[1].toFloat()
                    val secondsAndMs = parts[2].split(".")
                    val seconds = secondsAndMs[0].toFloat()
                    val ms = if (secondsAndMs.size > 1) {
                        ("0.${secondsAndMs[1]}").toFloat() * 1000
                    } else {
                        0f
                    }
                    ((hours * 3600 + minutes * 60 + seconds) * 1000 + ms).toLong()
                }
                durationStr.contains(":") && durationStr.split(":").size == 2 -> {
                    // MM:SS
                    val parts = durationStr.split(":")
                    val minutes = parts[0].toFloat()
                    val seconds = parts[1].toFloat()
                    ((minutes * 60 + seconds) * 1000).toLong()
                }
                else -> {
                    // 纯秒数或毫秒数
                    val value = durationStr.toFloat()
                    if (value > 100000) {
                        // 可能是毫秒
                        value.toLong()
                    } else {
                        // 秒
                        (value * 1000).toLong()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析时长失败: $durationStr", e)
            0L
        }
    }

    /**
     * 处理连接命令
     */
    private fun handleConnectionCommand(session: IHTTPSession): Response {
        Log.d(TAG, "收到连接命令")

        val soapResponse = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                       s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        return newFixedLengthResponse(
            Response.Status.OK,
            "text/xml; charset=\"utf-8\"",
            soapResponse
        )
    }

    /**
     * 获取设备描述文件
     * 伪装成乐播设备（小米电视内置乐播），提高Bilibili兼容性
     * 参考：https://github.com/xfangfang/wiliwili/issues/30
     */
    private fun getDeviceDescription(): String {
        val localIp = getLocalIpAddress()
        return """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
                <specVersion>
                    <major>1</major>
                    <minor>0</minor>
                </specVersion>
                <device>
                    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                    <friendlyName>小米电视(乐播投屏)</friendlyName>
                    <manufacturer>Xiaomi</manufacturer>
                    <manufacturerURL>http://www.mi.com/</manufacturerURL>
                    <modelName>Lelink Player</modelName>
                    <modelNumber>MDZ-16-AB</modelNumber>
                    <modelDescription>Xiaomi TV with Lelink Cast Support</modelDescription>
                    <modelURL>http://www.mi.com/mitv4</modelURL>
                    <serialNumber>${System.currentTimeMillis() shr 32}</serialNumber>
                    <UDN>uuid:$DEVICE_UUID</UDN>
                    <presentationURL>http://$localIp:49152/</presentationURL>
                    <dlna:X_DLNACAP/>
                    <dlna:X_DLNADOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">DMR-1.50</dlna:X_DLNADOC>
                    <serviceList>
                        <service>
                            <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                            <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                            <SCPDURL>/AVTransport.xml</SCPDURL>
                            <controlURL>/control</controlURL>
                            <eventSubURL>/event</eventSubURL>
                        </service>
                        <service>
                            <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                            <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                            <SCPDURL>/RenderingControl.xml</SCPDURL>
                            <controlURL>/control</controlURL>
                            <eventSubURL>/event</eventSubURL>
                        </service>
                        <service>
                            <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
                            <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
                            <SCPDURL>/ConnectionManager.xml</SCPDURL>
                            <controlURL>/connection</controlURL>
                            <eventSubURL>/event</eventSubURL>
                        </service>
                    </serviceList>
                </device>
            </root>
        """.trimIndent()
    }

    /**
     * 获取本机IP地址
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (networkInterface in java.util.Collections.list(interfaces)) {
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = java.util.Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return address.hostAddress ?: ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本机IP失败", e)
        }
        return "192.168.1.1"
    }

    /**
     * 获取服务描述文件 (SCPD)
     */
    private fun getServiceDescription(): String {
        return """
            <?xml version="1.0"?>
            <scpd xmlns="urn:schemas-upnp-org:service-1-0">
                <specVersion>
                    <major>1</major>
                    <minor>0</minor>
                </specVersion>
                <actionList>
                    <action>
                        <name>SetAVTransportURI</name>
                        <argumentList>
                            <argument>
                                <name>InstanceID</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable>
                            </argument>
                            <argument>
                                <name>CurrentURI</name>
                                <direction>in</direction>
                                <relatedStateVariable>AVTransportURI</relatedStateVariable>
                            </argument>
                            <argument>
                                <name>CurrentURIMetaData</name>
                                <direction>in</direction>
                                <relatedStateVariable>AVTransportURIMetaData</relatedStateVariable>
                            </argument>
                        </argumentList>
                    </action>
                    <action>
                        <name>GetTransportInfo</name>
                        <argumentList>
                            <argument>
                                <name>InstanceID</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable>
                            </argument>
                        </argumentList>
                    </action>
                    <action>
                        <name>Play</name>
                        <argumentList>
                            <argument>
                                <name>InstanceID</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable>
                            </argument>
                            <argument>
                                <name>Speed</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_Speed</relatedStateVariable>
                            </argument>
                        </argumentList>
                    </action>
                    <action>
                        <name>Pause</name>
                        <argumentList>
                            <argument>
                                <name>InstanceID</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable>
                            </argument>
                        </argumentList>
                    </action>
                    <action>
                        <name>Stop</name>
                        <argumentList>
                            <argument>
                                <name>InstanceID</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable>
                            </argument>
                        </argumentList>
                    </action>
                    <action>
                        <name>Seek</name>
                        <argumentList>
                            <argument>
                                <name>InstanceID</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable>
                            </argument>
                            <argument>
                                <name>Unit</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_SeekMode</relatedStateVariable>
                            </argument>
                            <argument>
                                <name>Target</name>
                                <direction>in</direction>
                                <relatedStateVariable>A_ARG_TYPE_SeekTarget</relatedStateVariable>
                            </argument>
                        </argumentList>
                    </action>
                </actionList>
                <serviceStateTable>
                    <stateVariable sendEvents="no">
                        <name>A_ARG_TYPE_InstanceID</name>
                        <dataType>ui4</dataType>
                    </stateVariable>
                    <stateVariable sendEvents="yes">
                        <name>TransportState</name>
                        <dataType>string</dataType>
                        <defaultValue>STOPPED</defaultValue>
                        <allowedValueList>
                            <allowedValue>STOPPED</allowedValue>
                            <allowedValue>PLAYING</allowedValue>
                            <allowedValue>PAUSED_PLAYBACK</allowedValue>
                        </allowedValueList>
                    </stateVariable>
                    <stateVariable sendEvents="yes">
                        <name>AVTransportURI</name>
                        <dataType>string</dataType>
                    </stateVariable>
                    <stateVariable sendEvents="yes">
                        <name>AVTransportURIMetaData</name>
                        <dataType>string</dataType>
                    </stateVariable>
                    <stateVariable sendEvents="no">
                        <name>A_ARG_TYPE_Speed</name>
                        <dataType>string</dataType>
                        <defaultValue>1</defaultValue>
                    </stateVariable>
                    <stateVariable sendEvents="no">
                        <name>A_ARG_TYPE_SeekMode</name>
                        <dataType>string</dataType>
                        <allowedValueList>
                            <allowedValue>REL_TIME</allowedValue>
                        </allowedValueList>
                    </stateVariable>
                    <stateVariable sendEvents="no">
                        <name>A_ARG_TYPE_SeekTarget</name>
                        <dataType>string</dataType>
                    </stateVariable>
                </serviceStateTable>
            </scpd>
        """.trimIndent()
    }

    /**
     * 提取反爬虫相关的HTTP头
     */
    private fun extractAntiCrawlerHeaders(headers: Map<String, String>): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // 提取常见的反爬虫HTTP头
        val antiCrawlerHeaders = listOf(
            "user-agent",
            "referer",
            "cookie",
            "accept",
            "accept-language",
            "accept-encoding"
        )

        for (header in antiCrawlerHeaders) {
            val value = headers[header] ?: headers[header.uppercase()]
                    ?: headers[header.split("-").joinToString("-") { it.capitalize() }]
            if (value != null) {
                result[header] = value
            }
        }

        // 如果没有User-Agent，使用常见的乐播投屏User-Agent
        if (!result.containsKey("user-agent")) {
            result["user-agent"] = "CastTalk/4.3.0 (Linux;Android 13) DLNA/1.0"
        }

        // 根据URL添加平台特定的Referer
        // （这里暂时留空，在收到URL后再处理）

        Log.d(TAG, "提取的反爬虫HTTP头: $result")
        return result
    }
}
