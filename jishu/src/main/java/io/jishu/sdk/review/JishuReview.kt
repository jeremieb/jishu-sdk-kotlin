package io.jishu.sdk.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import io.jishu.sdk.logging.JishuLogger
import io.jishu.sdk.network.JishuClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

internal object JishuReview {

    /** Pure eligibility check — no side effects, no network calls. */
    fun isEligible(
        config: ReviewConfig,
        store: ReviewStore,
        appId: String,
        bypassTimingGates: Boolean = false,
    ): Boolean {
        val launchCount       = store.launchCount(appId)
        val promptCount       = store.promptCount(appId)
        val installDate       = store.installDate(appId)
        val lastPromptMs      = store.lastPromptDate(appId)
        val now               = System.currentTimeMillis()

        val daysSinceCooldown = lastPromptMs?.let { TimeUnit.MILLISECONDS.toDays(now - it) }
        val daysSinceInstall  = if (installDate != 0L) TimeUnit.MILLISECONDS.toDays(now - installDate) else null

        // ── Verbose eligibility table ────────────────────────────────────
        JishuLogger.review("Review status — launch #$launchCount:")

        JishuLogger.review("  launches   " + when {
            bypassTimingGates || config.minLaunches == 0 -> "—"
            else -> "$launchCount / ${config.minLaunches}"
        })
        JishuLogger.review("  days       " + when {
            bypassTimingGates || config.minDaysSinceInstall == 0 -> "—"
            daysSinceInstall == null -> "unknown"
            else -> "$daysSinceInstall / ${config.minDaysSinceInstall} days"
        })
        JishuLogger.review("  cooldown   " + when {
            daysSinceCooldown == null -> "—"
            else -> "$daysSinceCooldown / ${config.cooldownDays} days  (${config.cooldownDays - daysSinceCooldown} to go)"
        })
        JishuLogger.review("  prompts    $promptCount / ${config.maxPromptsPerDevice}")
        // ─────────────────────────────────────────────────────────────────

        if (!config.enabled) {
            JishuLogger.review("  → not eligible — feature disabled")
            return false
        }
        if (promptCount >= config.maxPromptsPerDevice) {
            JishuLogger.review("  → not eligible — max prompts reached")
            return false
        }
        if (!bypassTimingGates && daysSinceCooldown != null && daysSinceCooldown < config.cooldownDays) {
            JishuLogger.review("  → not eligible — cooldown (${config.cooldownDays - daysSinceCooldown} days remaining)")
            return false
        }

        val launchesMet = bypassTimingGates || config.minLaunches == 0 || launchCount >= config.minLaunches
        val daysMet     = bypassTimingGates || config.minDaysSinceInstall == 0 ||
                          (daysSinceInstall != null && daysSinceInstall >= config.minDaysSinceInstall)
        val eligible    = if (config.triggerLogic == "OR") launchesMet || daysMet else launchesMet && daysMet

        if (!eligible) {
            val reason = listOfNotNull(
                "min launches not met".takeIf { !launchesMet },
                "min days not met".takeIf { !daysMet },
            ).joinToString(", ")
            JishuLogger.review("  → not eligible — $reason")
        } else {
            JishuLogger.review("  → eligible ✓")
        }

        return eligible
    }

    /**
     * Full prompt flow.
     * The Activity reference is used only within the scope of this suspend call and is never stored.
     */
    suspend fun runPromptFlow(
        config: ReviewConfig,
        store: ReviewStore,
        client: JishuClient,
        appId: String,
        uiHandler: JishuReviewUIHandler?,
        activity: Activity,
    ): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }

        // 1. Present UI
        val result: ReviewPromptResult = if (uiHandler != null) {
            uiHandler.presentReviewPrompt(
                title    = config.promptTitle.ifEmpty { "Enjoying the app?" },
                question = config.promptQuestion.ifEmpty { "We'd love to hear what you think." },
            ).also {
                // Let the custom dialog finish dismissing before the Play review prompt tries to present.
                delay(400)
            }
        } else {
            DefaultReviewAlertPresenter.present(activity, config)
        }

        // 2. Log shown after the prompt was actually presented.
        client.logReviewEvent(appId = appId, eventType = "shown", platform = "android", rating = null)

        // 3. Dismissed without rating
        if (result.dismissed || result.rating == null) {
            client.logReviewEvent(appId = appId, eventType = "dismissed", platform = "android", rating = null)
            store.recordPromptShown(appId)
            return true
        }

        val rating = result.rating

        // 4. Log rating
        client.logReviewEvent(appId = appId, eventType = "rating_given", platform = "android", rating = rating)

        // 5. Positive path — Google Play In-App Review
        if (rating >= config.ratingThreshold) {
            try {
                val manager = ReviewManagerFactory.create(activity)
                val reviewInfo = manager.requestReviewFlow().await()
                manager.launchReviewFlow(activity, reviewInfo).await()
                client.logReviewEvent(appId = appId, eventType = "native_requested", platform = "android", rating = null)
            } catch (e: Exception) {
                JishuLogger.error("Play review flow failed: ${e.message}")
            }
        }

        // 6. Negative path — capture feedback
        if (rating < config.ratingThreshold && config.captureFeedbackOnNegative) {
            val feedback = result.feedbackMessage.orEmpty()
            if (feedback.isNotEmpty()) {
                // sendReviewFeedback auto-logs feedback_sent on the server — no second event call needed
                client.sendReviewFeedback(appId = appId, body = feedback)
            }
        }

        // 7. Update local state
        store.recordPromptShown(appId)
        return true
    }
}
