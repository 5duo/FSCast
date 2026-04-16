package com.example.fscastremote.model

/**
 * 车机连接信息
 */
data class ConnectionInfo(
    val name: String = "FSCast",
    val ip: String = "",
    val port: Int = 19880,
    val version: Int = 1
)
