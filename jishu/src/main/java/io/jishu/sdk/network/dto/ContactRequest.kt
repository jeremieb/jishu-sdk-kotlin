package io.jishu.sdk.network.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class ContactRequest(
    val senderName: String? = null,
    val senderEmail: String,
    val subject: String? = null,
    val body: String,
    val userId: String? = null,
    val platform: String
)
