package io.jishu.sdk.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import io.jishu.sdk.logging.JishuLogger
import io.jishu.sdk.network.JishuClient
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

internal object JishuReview {

    /** Pure eligibility check — no side effects, no network calls. */
    fun isEligible(
        config: ReviewConfig,
        store: ReviewStore,
        bypassTimingGates: Boolean = false,
    ): Boolean {
        if (!config.enabled) return false
        if (store.promptCount >= config.maxPromptsPerDevice) return false

        if (!bypassTimingGates) {
            store.lastPromptDate?.let { lastMs ->
                val daysSince = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastMs)
                if (daysSince < config.cooldownDays) return false
            }
        }

        val launchCount = store.launchCount
        val installDate = store.installDate
        val launchesMet = when {
            bypassTimingGates -> true
            config.minLaunches == 0 -> true
            else -> launchCount >= config.minLaunches
        }
        val daysMet = when {
            bypassTimingGates -> true
            config.minDaysSinceInstall == 0 -> true
            installDate == 0L -> false
            else -> {
                val daysSince = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installDate)
                daysSince >= config.minDaysSinceInstall
            }
        }

        return if (config.triggerLogic == "OR") launchesMet || daysMet else launchesMet && daysMet
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
    ) {
        // 1. Log shown
        client.logReviewEvent(appId = appId, eventType = "shown", platform = "android", rating = null)

        // 2. Present UI
        val result: ReviewPromptResult = if (uiHandler != null) {
            uiHandler.presentReviewPrompt(
                title    = config.promptTitle.ifEmpty { "Enjoying the app?" },
                question = config.promptQuestion.ifEmpty { "We'd love to hear what you think." },
            )
        } else {
            DefaultReviewAlertPresenter.present(activity, config)
        }

        // 3. Dismissed without rating
        if (result.dismissed || result.rating == null) {
            client.logReviewEvent(appId = appId, eventType = "dismissed", platform = "android", rating = null)
            return
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
        store.recordPromptShown()
    }
}
