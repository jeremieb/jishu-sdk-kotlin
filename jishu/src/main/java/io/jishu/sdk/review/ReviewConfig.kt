package io.jishu.sdk.review

import kotlinx.serialization.Serializable

@Serializable
data class ReviewConfig(
    val enabled: Boolean = false,
    val triggerMode: String = "auto",
    val minLaunches: Int = 5,
    val minDaysSinceInstall: Int = 3,
    val triggerLogic: String = "AND",
    val cooldownDays: Int = 90,
    val maxPromptsPerDevice: Int = 3,
    val promptTitle: String = "",
    val promptQuestion: String = "",
    /** Ratings >= this value go to the Play In-App Review dialog; below go to the feedback prompt. */
    val ratingThreshold: Int = 4,
    val feedbackPrompt: String = "",
    val captureFeedbackOnNegative: Boolean = true,
) {
    companion object {
        /** Permissive fallback used when the network fails during a manual trigger. */
        val manualFallback = ReviewConfig(
            enabled = true,
            triggerMode = "manual",
            minLaunches = 0,
            minDaysSinceInstall = 0,
            triggerLogic = "OR",
            cooldownDays = 0,
            maxPromptsPerDevice = Int.MAX_VALUE,
            promptTitle = "",
            promptQuestion = "",
            ratingThreshold = 4,
            feedbackPrompt = "",
            captureFeedbackOnNegative = true,
        )
    }
}
