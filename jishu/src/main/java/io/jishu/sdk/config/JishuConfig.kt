package io.jishu.sdk.config

internal data class JishuConfig(
    val baseUrl: String,
    val apiToken: String,
    val appId: String,
    val environment: String?,
)
