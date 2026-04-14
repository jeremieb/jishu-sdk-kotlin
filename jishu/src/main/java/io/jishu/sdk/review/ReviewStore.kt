package io.jishu.sdk.review

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ReviewStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val installDate: Long get() = prefs.getLong(KEY_INSTALL_DATE, 0L)
    val launchCount: Int get() = prefs.getInt(KEY_LAUNCH_COUNT, 0)
    val lastPromptDate: Long? get() = prefs.getLong(KEY_LAST_PROMPT_DATE, 0L).takeIf { it > 0L }
    val promptCount: Int get() = prefs.getInt(KEY_PROMPT_COUNT, 0)

    fun setInstallDateIfNeeded() {
        if (prefs.getLong(KEY_INSTALL_DATE, 0L) == 0L) {
            prefs.edit().putLong(KEY_INSTALL_DATE, System.currentTimeMillis()).apply()
        }
    }

    fun incrementLaunchCount() {
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount + 1).apply()
    }

    fun recordPromptShown() {
        prefs.edit()
            .putLong(KEY_LAST_PROMPT_DATE, System.currentTimeMillis())
            .putInt(KEY_PROMPT_COUNT, promptCount + 1)
            .apply()
    }

    /** Returns cached config if within the 1-hour TTL, otherwise null. */
    fun cachedConfig(): ReviewConfig? {
        val cachedAt = prefs.getLong(KEY_CONFIG_CACHED_AT, 0L)
        if (cachedAt == 0L) return null
        if (System.currentTimeMillis() - cachedAt > TTL_MS) return null
        val encoded = prefs.getString(KEY_CONFIG_JSON, null) ?: return null
        return try { json.decodeFromString<ReviewConfig>(encoded) } catch (_: Exception) { null }
    }

    fun cacheConfig(config: ReviewConfig) {
        prefs.edit()
            .putString(KEY_CONFIG_JSON, json.encodeToString(config))
            .putLong(KEY_CONFIG_CACHED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Clears the cached config, forcing a fresh fetch on the next call. */
    fun invalidateConfigCache() {
        prefs.edit()
            .remove(KEY_CONFIG_JSON)
            .remove(KEY_CONFIG_CACHED_AT)
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
