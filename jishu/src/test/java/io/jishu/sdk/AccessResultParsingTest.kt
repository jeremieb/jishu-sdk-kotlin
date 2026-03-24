package io.jishu.sdk

import io.jishu.sdk.model.MatchType
import io.jishu.sdk.network.dto.AccessResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessResultParsingTest {

    @Test
    fun `granted true with device match parses correctly`() {
        val dto = AccessResultDto(
            granted = true,
            grantId = "grant_abc",
            matchType = "device",
            expiresAt = "2026-04-24T12:00:00.000Z",
            serverTime = "2026-03-24T12:00:00.000Z"
        )
        val result = dto.toAccessResult()
        assertTrue(result.granted)
        assertEquals("grant_abc", result.grantId)
        assertEquals(MatchType.DEVICE, result.matchType)
        assertNotNull(result.expiresAt)
        assertNotNull(result.serverTime)
    }

    @Test
    fun `granted false with none match parses correctly`() {
        val dto = AccessResultDto(
            granted = false,
            grantId = null,
            matchType = "none",
            expiresAt = null,
            serverTime = "2026-03-24T12:00:00.000Z"
        )
        val result = dto.toAccessResult()
        assertTrue(!result.granted)
        assertNull(result.grantId)
        assertEquals(MatchType.NONE, result.matchType)
        assertNull(result.expiresAt)
    }

    @Test
    fun `user match type parses correctly`() {
        val dto = AccessResultDto(
            granted = true,
            grantId = "grant_xyz",
            matchType = "user",
            expiresAt = "2026-05-01T00:00:00.000Z",
            serverTime = "2026-03-24T12:00:00.000Z"
        )
        assertEquals(MatchType.USER, dto.toAccessResult().matchType)
    }

    @Test
    fun `unknown match type falls back to NONE`() {
        val dto = AccessResultDto(
            granted = false,
            grantId = null,
            matchType = "unexpected_value",
            expiresAt = null,
            serverTime = "2026-03-24T12:00:00.000Z"
        )
        assertEquals(MatchType.NONE, dto.toAccessResult().matchType)
    }
}
