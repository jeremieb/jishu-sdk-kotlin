package io.jishu.sdk

import io.jishu.sdk.cache.AccessCache
import io.jishu.sdk.model.AccessResult
import io.jishu.sdk.model.MatchType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AccessCacheTest {

    private fun grantedResult(expiresAt: Instant? = Instant.parse("2099-01-01T00:00:00Z")) =
        AccessResult(
            granted = true,
            grantId = "g1",
            matchType = MatchType.DEVICE,
            expiresAt = expiresAt,
            serverTime = Instant.now()
        )

    @Test
    fun `cache returns stored result before expiry`() {
        val cache = AccessCache()
        val result = grantedResult()
        cache.put("key1", result)
        assertNotNull(cache.get("key1"))
    }

    @Test
    fun `cache returns null for missing key`() {
        val cache = AccessCache()
        assertNull(cache.get("missing"))
    }

    @Test
    fun `cache returns null after clear`() {
        val cache = AccessCache()
        cache.put("key1", grantedResult())
        cache.clear()
        assertNull(cache.get("key1"))
    }

    @Test
    fun `cache evicts entry when expiresAt is in the past`() {
        val cache = AccessCache()
        val expired = grantedResult(expiresAt = Instant.parse("2000-01-01T00:00:00Z"))
        cache.put("key1", expired)
        assertNull(cache.get("key1"))
    }
}
