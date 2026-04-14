package com.example.floatingscreencasting.dlna

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.InetAddress
import javax.xml.parsers.DocumentBuilderFactory

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
            Log.d(TAG, "正在启动HTTP服务器...")
            super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "HTTP服务器已启动，监听所有接口")
            Log.d(TAG, "服务器端口: 7676")
        } catch (e: Exception) {
            Log.e(TAG, "启动HTTP服务器失败", e)
            throw e
        }
    }

    private var onPlayCommand: ((String) -> Unit)? = null
    private var onStopCommand: (() -> Unit)? = null
    private var onPauseCommand: (() -> Unit)? = null
    private var onSeekCommand: ((String) -> Unit)? = null

    /**
     * 设置播放命令回调
     */
    fun setPlayCommand(callback: (String) -> Unit) {
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
                Log.d(TAG, "Metadata前200字符: ${metadata.take(200)}")

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

                // 异步处理播放命令
                CoroutineScope(Dispatchers.Main).launch {
                    onPlayCommand?.invoke(uri)
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

                CoroutineScope(Dispatchers.Main).launch {
                    onPlayCommand?.invoke("")
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
                Log.d(TAG, "收到Stop命令")
                transportState = "STOPPED"

                CoroutineScope(Dispatchers.Main).launch {
                    onStopCommand?.invoke()
                }

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

                CoroutineScope(Dispatchers.Main).launch {
                    onSeekCommand?.invoke(target)
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
     * 从XML中提取标签值
     */
    private fun extractValue(xml: String, tagName: String): String {
        val pattern = "<(?:ns0:)?$tagName[^>]*>(.*?)</(?:ns0:)?$tagName>".toRegex()
        val match = pattern.find(xml)
        return match?.groupValues?.get(1)?.trim() ?: ""
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
}
