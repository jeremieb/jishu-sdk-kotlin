package io.jishu.sdk

import android.content.Context
import io.jishu.sdk.cache.AccessCache
import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.identity.DeviceIdStore
import io.jishu.sdk.logging.JishuLogger
import io.jishu.sdk.model.AccessResult
import io.jishu.sdk.network.JishuClient

object Jishu {

    private var deviceIdStore: DeviceIdStore? = null
    private var client: JishuClient? = null
    private var cache: AccessCache? = null

    /**
     * Initializes the SDK. Call this once from your Application.onCreate() or
     * before any other Jishu call.
     *
     * @param context      Application context.
     * @param baseUrl      Root origin of your Jishu server, e.g. "https://jishu.page".
     * @param apiToken     API token from Account → API access (never log or expose this).
     * @param appId        Your app ID from the Jishu dashboard.
     * @param environment  Optional: "production", "staging", "testflight", or "internal".
     * @param enableDebugLogs  Set to true during development to see SDK log output.
     */
    fun configure(
        context: Context,
        baseUrl: String,
        apiToken: String,
        appId: String,
        environment: String? = null,
        enableDebugLogs: Boolean = false
    ) {
        JishuLogger.debugEnabled = enableDebugLogs
        val cfg = JishuConfig(
            baseUrl = baseUrl.trimEnd('/'),
            apiToken = apiToken,
            appId = appId,
            environment = environment
        )
        deviceIdStore = DeviceIdStore(context.applicationContext)
        client = JishuClient(cfg)
        cache = AccessCache()
        JishuLogger.d("Jishu SDK configured. appId=${cfg.appId}")
    }

    /**
     * A stable device-scoped identifier generated once and persisted locally.
     * Use this as the identity for unauthenticated users.
     *
     * Warning: reinstalling the app creates a new ID, invalidating any promo
     * grant tied to the previous one. Use [externalUserId] in [checkAccess]
     * when your app has authenticated users.
     */
    val displayUserID: String
        get() = deviceIdStore?.getOrCreate()
            ?: error("Jishu not configured. Call Jishu.configure() first.")

    /**
     * Checks whether this device/user has an active promo access grant.
     *
     * Results are cached for up to 5 minutes (or until the grant expires).
     * Negative results are never cached.
     *
     * @param externalUserId  Your authenticated user's stable ID. Pass null to
     *                        fall back to the device-scoped [displayUserID].
     */
    suspend fun checkAccess(externalUserId: String? = null): AccessResult {
        val store = deviceIdStore ?: error("Jishu not configured. Call Jishu.configure() first.")
        val c = client ?: error("Jishu not configured. Call Jishu.configure() first.")
        val ac = cache ?: error("Jishu not configured. Call Jishu.configure() first.")

        val deviceId = store.getOrCreate()
        val cacheKey = externalUserId ?: deviceId

        ac.get(cacheKey)?.let { cached ->
            JishuLogger.d("Cache hit for key=$cacheKey")
            return cached
        }

        val result = c.checkAccess(deviceId = deviceId, externalUserId = externalUserId)

        if (result.granted) {
            ac.put(cacheKey, result)
        }

        return result
    }
}
