package io.jishu.sdk.review

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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

        // Star rating dialog with an explicit five-star picker.
        val rating: Int? = suspendCoroutine { continuation ->
            val density = activity.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            var selectedRating = 0
            val starViews = mutableListOf<TextView>()

            fun updateStars() {
                starViews.forEachIndexed { index, starView ->
                    starView.text = if (index < selectedRating) "\u2605" else "\u2606"
                }
            }

            val starRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(8), dp(20), dp(8))
            }

            repeat(5) { index ->
                val starValue = index + 1
                val starView = TextView(activity).apply {
                    text = "\u2606"
                    textSize = 32f
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    setOnClickListener {
                        selectedRating = starValue
                        updateStars()
                    }
                }
                starViews += starView
                starRow.addView(starView)
            }

            val dialog = AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(question)
                .setView(starRow)
                .setPositiveButton("Submit", null)
                .setNegativeButton("Not now") { _, _ -> continuation.resume(null) }
                .setOnCancelListener { continuation.resume(null) }
                .create()

            dialog.setOnShowListener {
                val submitButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                submitButton.isEnabled = selectedRating > 0
                submitButton.setOnClickListener {
                    if (selectedRating > 0) {
                        continuation.resume(selectedRating)
                        dialog.dismiss()
                    }
                }

                starViews.forEachIndexed { index, starView ->
                    starView.setOnClickListener {
                        selectedRating = index + 1
                        updateStars()
                        submitButton.isEnabled = true
                    }
                }
            }

            dialog.show()
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
