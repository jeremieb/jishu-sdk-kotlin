package io.jishu.sdk

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import io.jishu.sdk.cache.AccessCache
import io.jishu.sdk.config.JishuConfig
import io.jishu.sdk.config.JishuEnvironment
import io.jishu.sdk.contact.ContactMessage
import io.jishu.sdk.feedback.Proposal
import io.jishu.sdk.feedback.ProposalStatus
import io.jishu.sdk.identity.DeviceIdStore
import io.jishu.sdk.identity.VoterTokenStore
import io.jishu.sdk.logging.JishuLogger
import io.jishu.sdk.JishuDebugLevel
import io.jishu.sdk.model.AccessResult
import io.jishu.sdk.network.JishuClient
import io.jishu.sdk.review.JishuReview
import io.jishu.sdk.review.JishuReviewUIHandler
import io.jishu.sdk.review.ReviewConfig
import io.jishu.sdk.review.ReviewStore
import java.util.concurrent.TimeUnit

object Jishu {

    private var deviceIdStore: DeviceIdStore? = null
    private var voterTokenStore: VoterTokenStore? = null
    private var client: JishuClient? = null
    private var cache: AccessCache? = null
    private var reviewStore: ReviewStore? = null

    /**
     * Optional custom UI handler for the review prompt.
     * Set before calling [trackLaunch]. When null, the SDK shows a default [android.app.AlertDialog].
     */
    var reviewUIHandler: JishuReviewUIHandler? = null

    /**
     * Initializes the SDK. Call this once from your Application.onCreate() or
     * before any other Jishu call.
     *
     * @param context      Application context.
     * @param server       Which backend to connect to. [JishuEnvironment.PRODUCTION] (default) or [JishuEnvironment.STAGING].
     * @param apiToken     API token from Account → API access (never log or expose this).
     * @param appId        Your app ID from the Jishu dashboard.
     * @param environment  Optional release channel: "production", "staging", "testflight", or "internal".
     * @param debugLevel   Controls Logcat output verbosity. [JishuDebugLevel.DEFAULT] prints errors only;
     *                     [JishuDebugLevel.VERBOSE] prints all SDK activity. Defaults to [JishuDebugLevel.DEFAULT].
     */
    fun configure(
        context: Context,
        server: JishuEnvironment = JishuEnvironment.PRODUCTION,
        apiToken: String,
        appId: String,
        environment: String? = null,
        debugLevel: JishuDebugLevel = JishuDebugLevel.DEFAULT
    ) {
        JishuLogger.level = debugLevel
        val cfg = JishuConfig(
            baseUrl = server.baseUrl,
            apiToken = apiToken,
            appId = appId,
            environment = environment
        )
        deviceIdStore = DeviceIdStore(context.applicationContext)
        voterTokenStore = VoterTokenStore(context.applicationContext)
        client = JishuClient(cfg)
        cache = AccessCache()
        reviewStore = ReviewStore(context.applicationContext)
        JishuLogger.configure("Jishu SDK configured — server=${server}, appId=${cfg.appId}")
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
     * Results are cached for up to 30 minutes (or until the grant expires).
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
            JishuLogger.info("Cache hit for key=$cacheKey")
            return cached
        }

        val result = c.checkAccess(deviceId = deviceId, externalUserId = externalUserId)

        if (result.granted) {
            ac.put(cacheKey, result)
        }

        return result
    }

    /**
     * Track a cold app launch and, when [triggerMode] is `"auto"`, show the review prompt
     * if eligibility conditions are met.
     *
     * Call once per cold start from [android.app.Application.onCreate] for the launch count,
     * passing the [Activity] from your main Activity's `onCreate` — **not** `onResume`,
     * which fires on every foreground transition.
     *
     * @param activity Required for showing the default AlertDialog and for the Play review API.
     */
    suspend fun trackLaunch(activity: Activity) {
        val c = client ?: return
        val rs = reviewStore ?: return
        rs.setInstallDateIfNeeded()
        rs.incrementLaunchCount()
        val bypassTimingGates = isDebugBuild(activity)

        val reviewConfig = runCatching { c.fetchReviewConfig(appId = c.appId, store = rs) }.getOrNull() ?: return
        if (reviewConfig.triggerMode != "auto") return
        if (bypassTimingGates) {
            JishuLogger.info("DEBUG Bypass: skipping review launch/day/cooldown gates")
        }
        if (!JishuReview.isEligible(reviewConfig, rs, bypassTimingGates = bypassTimingGates)) return

        JishuReview.runPromptFlow(
            config    = reviewConfig,
            store     = rs,
            client    = c,
            appId     = c.appId,
            uiHandler = reviewUIHandler,
            activity  = activity,
        )
    }

    /**
     * Manually trigger the review flow at a meaningful moment in your app.
     *
     * Always records the launch (increments launch count and sets install date on first call).
     * The SDK still respects [cooldownDays] and [maxPromptsPerDevice].
     * Use this when [triggerMode] is `"manual"`.
     *
     * @param activity Required for showing the default AlertDialog and for the Play review API.
     * @return `true` if the prompt was shown.
     */
    suspend fun requestReviewIfEligible(activity: Activity): Boolean {
        val c = client ?: return false
        val rs = reviewStore ?: return false
        val bypassTimingGates = isDebugBuild(activity)
        // Always record the launch — even in manual mode
        rs.setInstallDateIfNeeded()
        rs.incrementLaunchCount()

        // Bypass the 1-hour cache so dashboard changes take effect immediately
        rs.invalidateConfigCache()

        val reviewConfig = runCatching { c.fetchReviewConfig(appId = c.appId, store = rs) }
            .getOrElse {
                JishuLogger.info("Could not fetch review config, using manual fallback")
                ReviewConfig.manualFallback
            }

        if (!reviewConfig.enabled) {
            JishuLogger.info("Review prompt is disabled in dashboard config")
            return false
        }
        if (rs.promptCount >= reviewConfig.maxPromptsPerDevice) {
            JishuLogger.info("Max prompts per device reached (${rs.promptCount}/${reviewConfig.maxPromptsPerDevice})")
            return false
        }
        if (bypassTimingGates) {
            JishuLogger.info("DEBUG Bypass: skipping review launch/day/cooldown gates")
        } else {
            rs.lastPromptDate?.let { lastMs ->
                val daysSince = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastMs)
                if (daysSince < reviewConfig.cooldownDays) {
                    JishuLogger.info("Cooldown not elapsed ($daysSince/${reviewConfig.cooldownDays} days)")
                    return false
                }
            }
        }

        JishuReview.runPromptFlow(
            config    = reviewConfig,
            store     = rs,
            client    = c,
            appId     = c.appId,
            uiHandler = reviewUIHandler,
            activity  = activity,
        )
        return true
    }

    private fun isDebugBuild(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
