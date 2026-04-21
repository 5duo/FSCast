package com.example.floatingscreencasting.domain.model

/**
 * 投屏请求领域模型
 * 包含DLNA投屏的所有必要信息
 */
data class CastingRequest(
    val uri: String,
    val title: String,
    val headers: Map<String, String> = emptyMap(),
    val metadata: Metadata? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 检查请求是否有效
     */
    fun isValid(): Boolean = uri.isNotBlank()

    /**
     * 获取内容类型
     */
    fun getContentType(): String {
        return metadata?.contentType ?: "video/*"
    }

    /**
     * 元数据
     */
    data class Metadata(
        val duration: Long = 0,
        val contentType: String = "video/*",
        val currentPosition: Long = 0
    ) {
        companion object {
            /**
             * 创建空的元数据
             */
            fun empty() = Metadata()
        }
    }

    companion object {
        /**
         * 创建一个空的投屏请求
         */
        fun empty() = CastingRequest(
            uri = "",
            title = ""
        )
    }
}
