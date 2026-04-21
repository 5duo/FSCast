package com.example.floatingscreencasting.utils

/**
 * DLNA相关常量
 * 统一管理所有DLNA、网络、缓存相关的配置值
 */
object DlnaConstants {

    // ==================== 端口配置 ====================

    /**
     * DLNA HTTP服务器端口
     */
    const val HTTP_PORT = 49152

    /**
     * WebSocket服务器端口
     */
    const val WEBSOCKET_PORT = 9999

    /**
     * SSDP多播端口
     */
    const val SSDP_PORT = 1900

    /**
     * SSDP多播地址
     */
    const val SSDP_ADDRESS = "239.255.255.250"

    // ==================== 超时配置 ====================

    /**
     * 设备发现超时时间（毫秒）
     */
    const val DISCOVERY_TIMEOUT_MS = 5000L

    /**
     * 连接超时时间（毫秒）
     */
    const val CONNECTION_TIMEOUT_MS = 10_000L

    /**
     * 读取超时时间（毫秒）
     */
    const val READ_TIMEOUT_MS = 30_000L

    /**
     * WebSocket连接超时时间（毫秒）
     */
    const val WEBSOCKET_TIMEOUT_MS = 10_000L

    // ==================== 缓存配置 ====================

    /**
     * 视频缓存大小（字节）
     * 500MB = 500 * 1024 * 1024
     */
    val VIDEO_CACHE_SIZE_BYTES = 500 * 1024 * 1024L

    /**
     * 视频缓存目录名称
     */
    const val VIDEO_CACHE_FOLDER_NAME = "video_cache"

    // ==================== 同步配置 ====================

    /**
     * 进度检查间隔（毫秒）
     * 车机端每5秒检查一次进度
     */
    const val PROGRESS_CHECK_INTERVAL_MS = 5000L

    /**
     * 进度同步阈值（毫秒）
     * 差异超过此值时才进行同步
     */
    const val SYNC_THRESHOLD_MS = 3000L

    /**
     * 同步启动等待时间（毫秒）
     * 车机发送play_and_seek后等待手机加载的时间
     */
    const val SYNC_STARTUP_WAIT_MS = 3000L

    // ==================== 日志标签 ====================

    const val TAG_DLNA_DMR = "DlnaRendererService"
    const val TAG_DLNA_DMC = "DlnaControlPoint"
    const val TAG_HTTP_SERVER = "DlnaHttpServer"
    const val TAG_SSDP = "SsdpServer"
    const val TAG_WEBSOCKET = "CarWebSocketServer"
    const val TAG_AUDIO_OUTPUT = "AudioOutputController"
    const val TAG_VIDEO_PRESENTATION = "VideoPresentation"

    // ==================== 设备信息 ====================

    /**
     * 设备名称
     */
    const val DEVICE_NAME = "FSCast"

    /**
     * 设备型号
     */
    const val DEVICE_MODEL = "FSCast-Car"

    /**
     * 设备制造商
     */
    const val DEVICE_MANUFACTURER = "FSCast"

    /**
     * 设备UUID前缀
     */
    const val DEVICE_UUID_PREFIX = "fsdcast-car"

    // ==================== 显示配置 ====================

    /**
     * 默认显示ID（驾驶屏）
     */
    const val DEFAULT_DISPLAY_ID = 2

    /**
     * 默认窗口宽度
     */
    const val DEFAULT_WINDOW_WIDTH = 480

    /**
     * 默认窗口高度
     */
    const val DEFAULT_WINDOW_HEIGHT = 270

    /**
     * 默认窗口X位置
     */
    const val DEFAULT_WINDOW_X = 720

    /**
     * 默认窗口Y位置
     */
    const val DEFAULT_WINDOW_Y = 220

    /**
     * 默认窗口透明度
     */
    const val DEFAULT_WINDOW_ALPHA = 1.0f

    // ==================== 播放配置 ====================

    /**
     * 播放历史保存间隔（毫秒）
     * 每10秒保存一次播放进度
     */
    const val PLAYBACK_HISTORY_SAVE_INTERVAL_MS = 10_000L

    /**
     * 默认播放速度
     */
    const val DEFAULT_PLAYBACK_SPEED = 1.0f

    /**
     * 最小播放速度
     */
    const val MIN_PLAYBACK_SPEED = 0.5f

    /**
     * 最大播放速度
     */
    const val MAX_PLAYBACK_SPEED = 2.0f

    // ==================== DLNA协议相关 ====================

    /**
     * DLNA设备类型
     */
    const val DLNA_DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"

    /**
     * DLNA服务类型
     */
    const val DLNA_SERVICE_TYPE_AVTRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val DLNA_SERVICE_TYPE_RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

    /**
     * SSDP搜索目标
     */
    const val SSDP_SEARCH_TARGET = "ssdp:all"

    /**
     * SSDP通知间隔（秒）
     */
    const val SSDP_NOTIFY_INTERVAL_SEC = 30
}
