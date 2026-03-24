package io.jishu.sdk.model

import java.time.Instant

data class AccessResult(
    val granted: Boolean,
    val grantId: String?,
    val matchType: MatchType,
    val expiresAt: Instant?,
    val serverTime: Instant
)

enum class MatchType {
    USER,
    DEVICE,
    NONE
}
