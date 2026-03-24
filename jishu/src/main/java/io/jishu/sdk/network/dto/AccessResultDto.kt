package io.jishu.sdk.network.dto

import io.jishu.sdk.model.AccessResult
import io.jishu.sdk.model.MatchType
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
internal data class AccessResultDto(
    val granted: Boolean,
    val grantId: String? = null,
    val matchType: String,
    val expiresAt: String? = null,
    val serverTime: String
) {
    fun toAccessResult() = AccessResult(
        granted = granted,
        grantId = grantId,
        matchType = when (matchType.lowercase()) {
            "user" -> MatchType.USER
            "device" -> MatchType.DEVICE
            else -> MatchType.NONE
        },
        expiresAt = expiresAt?.let { Instant.parse(it) },
        serverTime = Instant.parse(serverTime)
    )
}
