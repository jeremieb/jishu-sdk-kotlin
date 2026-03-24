package io.jishu.sdk.network.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class CheckAccessRequest(
    val appId: String,
    val platform: String = "android",
    val externalUserId: String? = null,
    val deviceId: String? = null,
    val environment: String? = null
)
