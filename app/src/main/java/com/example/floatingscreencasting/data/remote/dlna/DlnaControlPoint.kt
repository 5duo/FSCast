package com.example.floatingscreencasting.data.remote.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * DLNA DMC (数字媒体控制器) 客户端
 * 用于发现和控制DLNA DMR设备
 */
class DlnaControlPoint(private val context: Context) {

    companion object {
        private const val TAG = "DlnaControlPoint"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val DISCOVERY_TIMEOUT = 5000

        // DLNA搜索目标
        private const val SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"

        // AVTransport服务URN
        private const val AVTRANSPORT_URN = "urn:schemas-upnp-org:service:AVTransport:1"
    }

    private var discoverySocket: DatagramSocket? = null
    private var isRunning = false
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val multicastLock: WifiManager.MulticastLock?
        get() = context.getSystemService(Context.WIFI_SERVICE)
            ?.let { (it as WifiManager).createMulticastLock("FSCast_DMC") }

    /**
     * 已发现的DLNA设备
     */
    data class DlnaDevice(
        val friendlyName: String,
        val location: String,
        val uuid: String,
        val manufacturer: String = "",
        val controlUrl: String = "",
        val ipAddress: String = ""
    )

    private val discoveredDevices = mutableMapOf<String, DlnaDevice>()

    // 设备列表变化回调
    var onDeviceListChanged: ((List<DlnaDevice>) -> Unit)? = null

    /**
     * 启动DLNA DMC客户端
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "DlnaControlPoint已经在运行")
            return
        }

        try {
            // 获取WiFi多播锁
            multicastLock?.acquire()

            // 创建UDP套接字
            discoverySocket = DatagramSocket()
            discoverySocket?.broadcast = true
            discoverySocket?.soTimeout = DISCOVERY_TIMEOUT

            isRunning = true

            Log.i(TAG, "DlnaControlPoint启动成功")

            // 启动设备发现协程
            discoveryScope.launch {
                while (isRunning) {
                    discoverDevices()
                    delay(30000) // 每30秒刷新一次设备列表
                }
            }

            // 启动响应监听协程
            discoveryScope.launch {
                listenForResponses()
            }

        } catch (e: Exception) {
            Log.e(TAG, "启动DlnaControlPoint失败", e)
            isRunning = false
            multicastLock?.release()
        }
    }

    /**
     * 发现DLNA设备
     */
    private suspend fun discoverDevices() {
        if (!isRunning) return

        try {
            val mSearchMessage = buildMSearchMessage()
            val message = mSearchMessage.toByteArray()
            val broadcastAddress = InetAddress.getByName(SSDP_ADDRESS)
            val packet = DatagramPacket(message, message.size, broadcastAddress, SSDP_PORT)

            discoverySocket?.send(packet)
            Log.d(TAG, "已发送SSDP M-SEARCH请求")

        } catch (e: Exception) {
            Log.e(TAG, "发送M-SEARCH失败", e)
        }
    }

    /**
     * 构建M-SEARCH消息
     */
    private fun buildMSearchMessage(): String {
        return """M-SEARCH * HTTP/1.1
HOST: $SSDP_ADDRESS:$SSDP_PORT
MAN: "ssdp:discover"
MX: 3
ST: $SEARCH_TARGET
USER-AGENT: FSCast/1.0 UPnP/1.1

""".trimIndent().replace("\n", "\r\n")
    }

    /**
     * 监听SSDP响应
     */
    private suspend fun listenForResponses() {
        val buffer = ByteArray(8192)

        while (isRunning) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                discoverySocket?.receive(packet)

                val response = String(packet.data, 0, packet.length)
                val address = packet.address.hostAddress

                if (address != null) {
                    Log.d(TAG, "收到SSDP响应 from $address")
                    parseSsdpResponse(response, address)
                }

            } catch (e: SocketTimeoutException) {
                // 超时是正常的，继续监听
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "监听SSDP响应失败", e)
                }
            }
        }
    }

    /**
     * 解析SSDP响应
     */
    private suspend fun parseSsdpResponse(response: String, ipAddress: String) {
        try {
            val lines = response.split("\r\n")
            var location = ""
            var usn = ""
            var st = ""

            for (line in lines) {
                when {
                    line.startsWith("LOCATION:", ignoreCase = true) ->
                        location = line.substring(9).trim()
                    line.startsWith("USN:", ignoreCase = true) ->
                        usn = line.substring(4).trim()
                    line.startsWith("ST:", ignoreCase = true) ->
                        st = line.substring(3).trim()
                }
            }

            // 只处理MediaRenderer设备
            if (st.contains("MediaRenderer", ignoreCase = true) && location.isNotEmpty()) {
                // 获取设备描述
                fetchDeviceDescription(location, usn, ipAddress)
            }

        } catch (e: Exception) {
            Log.e(TAG, "解析SSDP响应失败", e)
        }
    }

    /**
     * 获取设备描述
     */
    private suspend fun fetchDeviceDescription(location: String, usn: String, ipAddress: String) {
        try {
            val request = Request.Builder()
                .url(location)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val xml = response.body?.string() ?: return
                parseDeviceDescription(xml, location, usn, ipAddress)
            }

        } catch (e: Exception) {
            Log.e(TAG, "获取设备描述失败: $location", e)
        }
    }

    /**
     * 解析设备描述XML
     */
    private fun parseDeviceDescription(xml: String, location: String, usn: String, ipAddress: String) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.setNamespaceAware(true)
            val xpp = factory.newPullParser()
            xpp.setInput(StringReader(xml))

            var friendlyName = ""
            var manufacturer = ""
            var udn = ""
            var avTransportControlUrl = ""

            var eventType = xpp.eventType
            var inDevice = false
            var inService = false
            var currentServiceType = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (xpp.name) {
                            "device" -> inDevice = true
                            "service" -> {
                                inService = true
                                currentServiceType = ""
                            }
                            "friendlyName" -> if (inDevice) friendlyName = xpp.nextText()
                            "manufacturer" -> if (inDevice) manufacturer = xpp.nextText()
                            "UDN" -> if (inDevice) udn = xpp.nextText()
                            "serviceType" -> if (inService) currentServiceType = xpp.nextText()
                            "controlURL" -> {
                                if (inService && currentServiceType.contains("AVTransport")) {
                                    avTransportControlUrl = xpp.nextText()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (xpp.name) {
                            "device" -> inDevice = false
                            "service" -> inService = false
                        }
                    }
                }
                eventType = xpp.next()
            }

            // 提取UUID
            val uuid = if (udn.startsWith("uuid:")) {
                udn.substring(5)
            } else {
                usn
            }

            // 构建完整controlURL
            val baseUrl = location.substringBeforeLast("/")
            val fullControlUrl = if (avTransportControlUrl.startsWith("/")) {
                baseUrl + avTransportControlUrl
            } else {
                avTransportControlUrl
            }

            val device = DlnaDevice(
                friendlyName = friendlyName,
                location = location,
                uuid = uuid,
                manufacturer = manufacturer,
                controlUrl = fullControlUrl,
                ipAddress = ipAddress
            )

            addDevice(device)

        } catch (e: Exception) {
            Log.e(TAG, "解析设备描述失败", e)
        }
    }

    /**
     * 添加或更新设备
     */
    private fun addDevice(device: DlnaDevice) {
        val isNew = !discoveredDevices.containsKey(device.uuid)
        discoveredDevices[device.uuid] = device

        if (isNew) {
            Log.i(TAG, "发现新设备: ${device.friendlyName} (${device.ipAddress})")
            onDeviceListChanged?.invoke(getDeviceList())
        }
    }

    /**
     * 获取设备列表
     */
    fun getDeviceList(): List<DlnaDevice> {
        return discoveredDevices.values.toList()
    }

    /**
     * 发送AVTransport命令
     */
    suspend fun sendAvTransportCommand(
        device: DlnaDevice,
        action: String,
        arguments: Map<String, String> = emptyMap()
    ): Boolean {
        try {
            val soapBody = buildSoapBody(action, arguments)
            val soapAction = "\"$AVTRANSPORT_URN#$action\""

            val request = Request.Builder()
                .url(device.controlUrl)
                .post(soapBody.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
                .addHeader("SOAPAction", soapAction)
                .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .build()

            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful

            Log.d(TAG, "发送AVTransport命令: $action, 成功: $success")
            return success

        } catch (e: Exception) {
            Log.e(TAG, "发送AVTransport命令失败: $action", e)
            return false
        }
    }

    /**
     * 构建SOAP消息体
     */
    private fun buildSoapBody(action: String, arguments: Map<String, String>): String {
        val argsXml = arguments.entries.joinToString("") { (key, value) ->
            "<$key>$value</$key>"
        }

        return """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:$action xmlns:u="$AVTRANSPORT_URN">
      $argsXml
    </u:$action>
  </s:Body>
</s:Envelope>"""
    }

    /**
     * 设置播放URI
     */
    suspend fun setAvTransportUri(device: DlnaDevice, uri: String, httpHeaders: Map<String, String> = emptyMap()): Boolean {
        // 将HTTP头信息编码到元数据中
        val metadata = if (httpHeaders.isNotEmpty()) {
            buildDlnaMetadata(uri, httpHeaders)
        } else {
            ""
        }

        return sendAvTransportCommand(device, "SetAVTransportURI", mapOf(
            "CurrentURI" to uri,
            "CurrentURIMetaData" to metadata
        ))
    }

    /**
     * 构建DLNA元数据（包含HTTP头信息）
     */
    private fun buildDlnaMetadata(uri: String, httpHeaders: Map<String, String>): String {
        // 将HTTP头转换为JSON格式，然后放入DLNA元数据
        val headersJson = org.json.JSONObject(httpHeaders)
        val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
  <item id="1000" parentID="-1" restricted="0">
    <dc:title>Video</dc:title>
    <upnp:class>object.item.videoItem</upnp:class>
    <res protocolInfo="http-get:*:video/mp4:*">$uri</res>
    <custom xmlns="http://www.fscast.com/headers">$headersJson</custom>
  </item>
</DIDL-Lite>"""
        return didl
    }

    /**
     * 播放
     */
    suspend fun play(device: DlnaDevice): Boolean {
        return sendAvTransportCommand(device, "Play", mapOf(
            "Speed" to "1"
        ))
    }

    /**
     * 暂停
     */
    suspend fun pause(device: DlnaDevice): Boolean {
        return sendAvTransportCommand(device, "Pause")
    }

    /**
     * 停止
     */
    suspend fun stop(device: DlnaDevice): Boolean {
        return sendAvTransportCommand(device, "Stop")
    }

    /**
     * 跳转
     */
    suspend fun seek(device: DlnaDevice, positionMs: Long): Boolean {
        // 格式化为HH:MM:SS或MM:SS
        val hours = positionMs / 3600000
        val minutes = (positionMs % 3600000) / 60000
        val seconds = (positionMs % 60000) / 1000

        val target = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        return sendAvTransportCommand(device, "Seek", mapOf(
            "Unit" to "REL_TIME",
            "Target" to target
        ))
    }

    /**
     * 停止DLNA DMC客户端
     */
    fun stop() {
        Log.i(TAG, "正在停止DlnaControlPoint...")
        isRunning = false

        discoverySocket?.close()
        discoveryScope.cancel()

        multicastLock?.release()

        discoveredDevices.clear()

        Log.i(TAG, "DlnaControlPoint已停止")
    }
}

// 向后兼容的类型别名
@Deprecated("使用 DlnaControlPoint 代替", ReplaceWith("DlnaControlPoint"))
typealias DlnaDmcClient = DlnaControlPoint
