# DLNA设备发现机制详解

## SSDP协议工作原理

### 1. 设备发现流程

```
┌──────────────┐                    ┌──────────────┐
│  手机APP     │                    │  车载设备    │
│ (DLNA控制器) │                    │ (DLNA渲染器) │
└──────┬───────┘                    └──────┬───────┘
       │                                  │
       │  1. 发送M-SEARCH搜索请求          │
       │  (多播到239.255.255.250:1900)    │
       │─────────────────────────────────>│
       │                                  │
       │                                  │ 2. 监听多播
       │                                  │
       │  3. 响应200 OK (包含设备信息)    │
       │<─────────────────────────────────│
       │                                  │
       │  4. 解析响应，获取设备描述XML URL │
       │                                  │
       │  5. 请求description.xml          │
       │─────────────────────────────────>│
       │                                  │
       │  6. 返回设备描述XML              │
       │<─────────────────────────────────│
       │  - 设备名称、型号、服务列表       │
       │                                  │
       │  7. 显示在投屏设备列表中         │
       │                                  │
```

### 2. 关键实现（SsdpServer.kt）

#### 定期发送NOTIFY消息
```kotlin
// 每5秒发送一次NOTIFY
private suspend fun sendNotifyPeriodically() {
    while (isRunning) {
        sendNotify()
        delay(5000)
    }
}
```

#### NOTIFY消息内容
```
NOTIFY * HTTP/1.1
HOST: 239.255.255.250:1900
CACHE-CONTROL: max-age=1800
LOCATION: http://192.168.112.222:49152/dlna/description.xml
SERVER: FloatingScreenCasting/1.0 UPnP/1.0 DLNADOC/1.50
NT: urn:schemas-upnp-org:device:MediaRenderer:1
NTS: ssdp:alive
USN: uuid:4d696e69-444c-4e61-5500-112233445566::urn:schemas-upnp-org:device:MediaRenderer:1
```

**关键字段**：
- `LOCATION`：设备描述文件的URL（包含IP地址）
- `NT`：设备类型（MediaRenderer）
- `USN`：唯一设备标识符

#### 监听并响应M-SEARCH
```kotlin
private suspend fun listenForMSearch() {
    while (isRunning) {
        // 接收多播消息
        multicastSocket?.receive(packet)

        // 如果是M-SEARCH请求，发送响应
        if (message.contains("M-SEARCH")) {
            sendMSearchResponse(packet.address, packet.port)
        }
    }
}
```

### 3. 设备描述文件（DlnaHttpServer.kt）

手机APP获取到LOCATION后，会请求这个XML：

```xml
<root xmlns="urn:schemas-upnp-org:device-1-0">
    <device>
        <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
        <friendlyName>Chromecast Ultra</friendlyName>  <!-- 显示名称 -->
        <manufacturer>Google Inc.</manufacturer>
        <modelName>Chromecast</modelName>
        <UDN>uuid:4d696e69-444c-4e61-5500-112233445566</UDN>
        <serviceList>
            <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/control</controlURL>
            </service>
        </serviceList>
    </device>
</root>
```

### 4. 为什么之前找不到设备？

可能的原因：
1. ❌ WiFi多播锁未获取
2. ❌ 绑定端口1900失败
3. ❌ NOTIFICATION消息未发送
4. ❌ 网络接口选择错误
5. ❌ 防火墙阻止多播

### 5. 当前解决方案

```kotlin
// 1. 获取WiFi多播锁
multicastLock = wifiManager.createMulticastLock("DLNA_SSDP").apply {
    acquire()
}

// 2. 绑定到1900端口
multicastSocket = MulticastSocket(SSDP_PORT).apply {
    setReuseAddress(true)
    joinGroup(InetAddress.getByName("239.255.255.250"))
}

// 3. 定期发送NOTIFY
sendNotify()  // 每5秒一次

// 4. 监听M-SEARCH
listenForMSearch()
```

## 2️⃣ IP地址变化问题

### 当前实现的问题

```kotlin
// ❌ 问题：IP地址在启动时获取一次
private val location = "http://${getLocalIpAddress()}:49152/dlna/description.xml"

// 如果IP从 192.168.112.222 变成 192.168.1.100
// 手机APP仍会尝试访问旧的IP地址！
```

### 改进方案：动态IP更新

需要监听网络变化，动态更新IP地址：

```kotlin
class SsdpServer(private val context: Context) {
    
    // 使用可变的location
    private var currentLocation: String
        get() = "http://${getLocalIpAddress()}:49152/dlna/description.xml"
    
    // 监听网络变化
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // 网络可用，重新发送NOTIFY
            GlobalScope.launch(Dispatchers.IO) {
                sendNotify()
            }
        }
        
        override fun onLost(network: Network) {
            // 网络断开
            Log.w(TAG, "网络断开")
        }
    }
    
    // 注册网络监听
    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
        cm.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            networkCallback
        )
    }
}
```

### 车辆特殊场景处理

**场景**：锁车后再解锁，IP地址变化

```
锁车前: IP = 192.168.112.222
   ↓ 锁车（设备休眠，WiFi断开）
解锁后: IP = 192.168.1.100  ❌ 新IP！
```

**解决方案**：

1. **方案1：监听网络变化**（推荐）
   - 注册网络状态回调
   - IP变化时重新发送NOTIFY
   - 确保LOCATION字段使用最新IP

2. **方案2：使用主机名**
   - 如果车机有固定主机名（如 `car-headunit.local`）
   - 使用mDNS解析
   - 不依赖IP地址

3. **方案3：快速重新发现**
   - 检测到网络恢复时
   - 立即发送多个NOTIFY消息
   - 确保手机APP快速发现

## 建议的改进代码

### 完整的网络监听实现

```kotlin
class SsdpServer(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as ConnectivityManager
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "网络连接可用")
            refreshNetworkInfo()
        }
        
        override fun onLost(network: Network) {
            Log.w(TAG, "网络连接断开")
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            Log.d(TAG, "网络能力变化")
            refreshNetworkInfo()
        }
    }
    
    private fun refreshNetworkInfo() {
        GlobalScope.launch(Dispatchers.IO) {
            val newIp = getLocalIpAddress()
            Log.d(TAG, "当前IP地址: $newIp")
            
            // 立即发送多次NOTIFY，确保被发现
            repeat(5) {
                sendNotify()
                delay(1000)
            }
        }
    }
    
    suspend fun start(): Boolean {
        // ... 现有代码 ...
        
        // 注册网络监听
        registerNetworkCallback()
        
        return true
    }
    
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        
        connectivityManager.registerNetworkCallback(
            request,
            networkCallback
        )
        
        Log.d(TAG, "网络监听已注册")
    }
    
    suspend fun stop() {
        // ... 现有停止代码 ...
        
        // 取消网络监听
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "取消网络监听失败", e)
        }
    }
}
```

## 测试建议

### 测试IP变化场景

1. **手动测试**：
   ```
   1. 启动应用，记录初始IP
   2. 锁车（让设备休眠）
   3. 解锁车
   4. 查看日志，确认IP已更新
   5. 在手机APP中刷新设备列表
   ```

2. **日志验证**：
   ```
   adb logcat | grep -E "SsdpServer|网络"
   
   应该看到：
   SsdpServer: 当前IP地址: 192.168.112.222
   SsdpServer: 网络连接断开
   SsdpServer: 网络连接可用
   SsdpServer: 当前IP地址: 192.168.1.100
   SsdpServer: 发送NOTIFY成功
   ```

## 总结

| 问题 | 当前方案 | 问题 | 改进方案 |
|------|---------|------|---------|
| 设备发现 | SSDP协议 | 可能不稳定 | 增加NOTIFY频率 |
| IP变化 | 启动时获取一次 | ❌ 不更新 | 监听网络变化 |

建议实施IP动态更新，以应对车辆锁车/解锁场景！
