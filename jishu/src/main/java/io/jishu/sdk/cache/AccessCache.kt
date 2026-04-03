package io.jishu.sdk.cache

import io.jishu.sdk.model.AccessResult
import java.time.Instant

internal class AccessCache {

    private val maxTtlMs = 30 * 60 * 1000L  // 30 minutes

    private data class Entry(val result: AccessResult, val fetchedAtMs: Long)

    private val store = mutableMapOf<String, Entry>()

    fun get(key: String): AccessResult? {
        val entry = store[key] ?: return null
        val now = System.currentTimeMillis()
        val expiresAtMs = entry.result.expiresAt?.toEpochMilli() ?: Long.MAX_VALUE
        val effectiveExpiry = minOf(entry.fetchedAtMs + maxTtlMs, expiresAtMs)
        if (now >= effectiveExpiry) {
            store.remove(key)
            return null
        }
        return entry.result
    }

    fun put(key: String, result: AccessResult) {
        store[key] = Entry(result, System.currentTimeMillis())
    }

    fun clear() {
        store.clear()
    }
}
