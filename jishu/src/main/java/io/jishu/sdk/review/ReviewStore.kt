package io.jishu.sdk.review

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ReviewStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private fun namespacedKey(base: String, appId: String) = "$base.$appId"

    fun installDate(appId: String): Long = prefs.getLong(namespacedKey(KEY_INSTALL_DATE, appId), 0L)
    fun launchCount(appId: String): Int = prefs.getInt(namespacedKey(KEY_LAUNCH_COUNT, appId), 0)
    fun lastPromptDate(appId: String): Long? = prefs.getLong(namespacedKey(KEY_LAST_PROMPT_DATE, appId), 0L).takeIf { it > 0L }
    fun promptCount(appId: String): Int = prefs.getInt(namespacedKey(KEY_PROMPT_COUNT, appId), 0)

    fun setInstallDateIfNeeded(appId: String) {
        val key = namespacedKey(KEY_INSTALL_DATE, appId)
        if (prefs.getLong(key, 0L) == 0L) {
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        }
    }

    fun incrementLaunchCount(appId: String) {
        prefs.edit().putInt(namespacedKey(KEY_LAUNCH_COUNT, appId), launchCount(appId) + 1).apply()
    }

    fun recordPromptShown(appId: String) {
        prefs.edit()
            .putLong(namespacedKey(KEY_LAST_PROMPT_DATE, appId), System.currentTimeMillis())
            .putInt(namespacedKey(KEY_PROMPT_COUNT, appId), promptCount(appId) + 1)
            .apply()
    }

    /** Returns cached config if within the 1-hour TTL, otherwise null. */
    fun cachedConfig(appId: String): ReviewConfig? {
        val cachedAt = prefs.getLong(namespacedKey(KEY_CONFIG_CACHED_AT, appId), 0L)
        if (cachedAt == 0L) return null
        if (System.currentTimeMillis() - cachedAt > TTL_MS) return null
        val encoded = prefs.getString(namespacedKey(KEY_CONFIG_JSON, appId), null) ?: return null
        return try { json.decodeFromString<ReviewConfig>(encoded) } catch (_: Exception) { null }
    }

    fun cacheConfig(config: ReviewConfig, appId: String) {
        prefs.edit()
            .putString(namespacedKey(KEY_CONFIG_JSON, appId), json.encodeToString(config))
            .putLong(namespacedKey(KEY_CONFIG_CACHED_AT, appId), System.currentTimeMillis())
            .apply()
    }

    /** Clears the cached config, forcing a fresh fetch on the next call. */
    fun invalidateConfigCache(appId: String) {
        prefs.edit()
            .remove(namespacedKey(KEY_CONFIG_JSON, appId))
            .remove(namespacedKey(KEY_CONFIG_CACHED_AT, appId))
            .apply()
    }

    companion object {
        private const val PREFS_NAME      = "io.jishu.sdk.review"
        private const val KEY_INSTALL_DATE    = "install_date"
        private const val KEY_LAUNCH_COUNT    = "launch_count"
        private const val KEY_LAST_PROMPT_DATE = "last_prompt_date"
        private const val KEY_PROMPT_COUNT    = "prompt_count"
        private const val KEY_CONFIG_JSON     = "config_json"
        private const val KEY_CONFIG_CACHED_AT = "config_cached_at"
        private const val TTL_MS = 3_600_000L // 1 hour
    }
}
