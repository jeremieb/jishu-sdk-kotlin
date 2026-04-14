package io.jishu.sdk.review

import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RatingBar
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface JishuReviewUIHandler {
    /**
     * Called when the SDK decides to show the review prompt.
     * Present a 1–5 star rating UI and return the user's response.
     */
    suspend fun presentReviewPrompt(title: String, question: String): ReviewPromptResult
}

data class ReviewPromptResult(
    /** Star rating given by the user (1–5). Null if dismissed without rating. */
    val rating: Int?,
    val dismissed: Boolean = false,
    val feedbackMessage: String? = null,
)

internal object DefaultReviewAlertPresenter {
    /**
     * Shows the default star rating dialog.
     * The Activity reference is used only within the scope of this suspend call and is never stored.
     */
    suspend fun present(activity: Activity, config: ReviewConfig): ReviewPromptResult {
        val title    = config.promptTitle.ifEmpty { "Enjoying the app?" }
        val question = config.promptQuestion.ifEmpty { "We'd love to hear what you think." }

        // Star rating dialog
        val rating: Int? = suspendCoroutine { continuation ->
            val ratingBar = RatingBar(activity).apply {
                numStars = 5
                stepSize = 1f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(question)
                .setView(ratingBar)
                .setPositiveButton("Submit") { _, _ ->
                    continuation.resume(ratingBar.rating.toInt().coerceIn(1, 5))
                }
                .setNegativeButton("Not now") { _, _ -> continuation.resume(null) }
                .setOnCancelListener { continuation.resume(null) }
                .show()
        }

        if (rating == null) return ReviewPromptResult(rating = null, dismissed = true)

        // Feedback text input for below-threshold ratings
        var feedbackMessage: String? = null
        if (rating < config.ratingThreshold && config.captureFeedbackOnNegative) {
            val prompt = config.feedbackPrompt.ifEmpty { "What could we improve?" }
            feedbackMessage = suspendCoroutine { continuation ->
                val editText = EditText(activity).apply {
                    hint = "Your feedback"
                    isSingleLine = false
                    minLines = 2
                    setPadding(48, 16, 48, 8)
                }
                AlertDialog.Builder(activity)
                    .setTitle(prompt)
                    .setView(editText)
                    .setPositiveButton("Send") { _, _ ->
                        val text = editText.text?.toString()?.trim()
                        continuation.resume(text?.ifEmpty { null })
                    }
                    .setNegativeButton("Skip") { _, _ -> continuation.resume(null) }
                    .setOnCancelListener { continuation.resume(null) }
                    .show()
            }
        }

        return ReviewPromptResult(rating = rating, feedbackMessage = feedbackMessage)
    }
}
