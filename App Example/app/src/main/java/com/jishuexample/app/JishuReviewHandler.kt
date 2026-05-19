package com.jishuexample.app

import androidx.compose.runtime.Stable
import io.jishu.sdk.review.JishuReviewUIHandler
import io.jishu.sdk.review.ReviewPromptResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Bridges the SDK's suspend-based [JishuReviewUIHandler] callback with Compose UI state.
 * Assign to [io.jishu.sdk.Jishu.reviewUIHandler] before calling trackLaunch.
 *
 * The [promptState] flow drives a ModalBottomSheet in the host composable.
 * Call [submit] or [dismiss] from the sheet to resume the SDK's coroutine.
 */
@Stable
class JishuReviewHandler : JishuReviewUIHandler {

    private val _promptState = MutableStateFlow<PromptState?>(null)
    val promptState: StateFlow<PromptState?> = _promptState.asStateFlow()

    private var continuation: CancellableContinuation<ReviewPromptResult>? = null

    data class PromptState(val title: String, val question: String)

    override suspend fun presentReviewPrompt(title: String, question: String): ReviewPromptResult {
        _promptState.value = PromptState(title, question)
        return suspendCancellableCoroutine { cont ->
            continuation = cont
            cont.invokeOnCancellation {
                _promptState.value = null
                continuation = null
            }
        }
    }

    fun submit(rating: Int) {
        _promptState.value = null
        continuation?.resume(ReviewPromptResult(rating = rating, dismissed = false))
        continuation = null
    }

    fun dismiss() {
        _promptState.value = null
        continuation?.resume(ReviewPromptResult(rating = null, dismissed = true))
        continuation = null
    }
}
