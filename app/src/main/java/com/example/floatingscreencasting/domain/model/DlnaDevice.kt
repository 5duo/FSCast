package com.example.floatingscreencasting.domain.model

/**
 * DLNA设备领域模型
 * 表示一个DLNA设备（如手机、电视等）
 */
data class DlnaDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val location: String,
    val isActive: Boolean = true
) {
    /**
     * 获取设备的完整地址
     */
    val fullAddress: String
        get() = "$host:$port"

    /**
     * 获取设备的描述URL
     */
    val descriptionUrl: String
        get() = "http://$fullAddress/description.xml"

    /**
     * 检查设备是否可用
     */
    fun isAvailable(): Boolean = isActive && port > 0

    companion object {
        /**
         * 创建一个空的DLNA设备
         */
        fun empty() = DlnaDevice(
            id = "",
            name = "",
            host = "",
            port = 0,
            location = "",
            isActive = false
        )
    }
}
