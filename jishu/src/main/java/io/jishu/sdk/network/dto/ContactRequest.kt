package io.jishu.sdk.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ContactRequest(
    val senderName: String? = null,
    val senderEmail: String,
    val subject: String? = null,
    val body: String,
    val userId: String? = null,
    val platform: String,
    @SerialName("osName") val osName: String? = null,
    @SerialName("osVersion") val osVersion: String? = null,
    @SerialName("deviceName") val deviceName: String? = null,
)
