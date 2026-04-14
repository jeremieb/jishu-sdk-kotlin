package io.jishu.sdk.network.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class ReviewEventRequest(
    val eventType: String,
    val platform: String,
    val rating: Int? = null,
)

@Serializable
internal data class ReviewFeedbackRequest(
    val body: String,
    val platform: String,
    val osName: String,
    val osVersion: String,
    val deviceName: String,
)
