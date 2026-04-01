package io.jishu.sdk

import android.content.Context
import io.jishu.sdk.cache.AccessCache
import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.contact.ContactMessage
import io.jishu.sdk.feedback.Proposal
import io.jishu.sdk.feedback.ProposalStatus
import io.jishu.sdk.identity.DeviceIdStore
import io.jishu.sdk.identity.VoterTokenStore
import io.jishu.sdk.logging.JishuLogger
import io.jishu.sdk.JishuDebugLevel
import io.jishu.sdk.model.AccessResult
import io.jishu.sdk.network.JishuClient

object Jishu {

    private var deviceIdStore: DeviceIdStore? = null
    private var voterTokenStore: VoterTokenStore? = null
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
     * @param debugLevel  Controls Logcat output verbosity. [JishuDebugLevel.DEFAULT] prints errors only;
     *                    [JishuDebugLevel.VERBOSE] prints all SDK activity. Defaults to [JishuDebugLevel.DEFAULT].
     */
    fun configure(
        context: Context,
        baseUrl: String,
        apiToken: String,
        appId: String,
        environment: String? = null,
        debugLevel: JishuDebugLevel = JishuDebugLevel.DEFAULT
    ) {
        JishuLogger.level = debugLevel
        val cfg = JishuConfig(
            baseUrl = baseUrl.trimEnd('/'),
            apiToken = apiToken,
            appId = appId,
            environment = environment
        )
        deviceIdStore = DeviceIdStore(context.applicationContext)
        voterTokenStore = VoterTokenStore(context.applicationContext)
        client = JishuClient(cfg)
        cache = AccessCache()
        JishuLogger.verbose("Jishu SDK configured. appId=${cfg.appId}")
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
     * Submits a contact form message from the app user.
     *
     * The message is associated with the [appId] supplied to [configure].
     * No API token is required — the endpoint is public and rate-limited by IP.
     *
     * @param message  The contact message to send.
     * @throws IllegalStateException if [configure] has not been called.
     */
    suspend fun sendContactMessage(message: ContactMessage) {
        val store = deviceIdStore ?: error("Jishu not configured. Call Jishu.configure() first.")
        val c = client ?: error("Jishu not configured. Call Jishu.configure() first.")
        c.sendContactMessage(message, displayUserId = store.getOrCreate())
    }

    /**
     * Fetch feature proposals for the configured app.
     *
     * @param sort   Either "votes" (default) or "recent".
     * @param status Proposal status filter. Defaults to [ProposalStatus.OPEN].
     * @throws IllegalStateException if [configure] has not been called.
     */
    suspend fun fetchProposals(
        sort: String = "votes",
        status: ProposalStatus = ProposalStatus.OPEN
    ): List<Proposal> {
        val c = client ?: error("Jishu not configured. Call Jishu.configure() first.")
        return c.fetchProposals(sort = sort, status = status)
    }

    /**
     * Submit a new feature proposal for the configured app.
     *
     * No authentication is required. The SDK uses [displayUserID] as the stable voter token.
     *
     * @throws IllegalStateException if [configure] has not been called.
     */
    suspend fun submitProposal(title: String, description: String? = null): Proposal {
        val vts = voterTokenStore ?: error("Jishu not configured. Call Jishu.configure() first.")
        val c = client ?: error("Jishu not configured. Call Jishu.configure() first.")
        return c.submitProposal(title = title, description = description, voterToken = vts.getOrCreate())
    }

    /**
     * Vote on a proposal for the configured app. Duplicate votes from the same device are ignored by the backend.
     *
     * @return The updated vote count.
     * @throws IllegalStateException if [configure] has not been called.
     */
    suspend fun vote(proposalId: String): Int {
        val vts = voterTokenStore ?: error("Jishu not configured. Call Jishu.configure() first.")
        val c = client ?: error("Jishu not configured. Call Jishu.configure() first.")
        return c.vote(proposalId = proposalId, voterToken = vts.getOrCreate())
    }

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
            JishuLogger.verbose("Cache hit for key=$cacheKey")
            return cached
        }

        val result = c.checkAccess(deviceId = deviceId, externalUserId = externalUserId)

        if (result.granted) {
            ac.put(cacheKey, result)
        }

        return result
    }
}
