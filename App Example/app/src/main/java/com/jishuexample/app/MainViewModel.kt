package com.jishuexample.app

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jishu.sdk.Jishu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    val userID: String = Jishu.displayUserID

    private val _externalUserID = MutableStateFlow("")
    val externalUserID: StateFlow<String> = _externalUserID

    private val _isGranted = MutableStateFlow(false)
    val isGranted: StateFlow<Boolean> = _isGranted

    private val _grantCheckMessage = MutableStateFlow("Run a grant check to see details.")
    val grantCheckMessage: StateFlow<String> = _grantCheckMessage

    private val _isCheckingGrant = MutableStateFlow(false)
    val isCheckingGrant: StateFlow<Boolean> = _isCheckingGrant

    private val _isRequestingReview = MutableStateFlow(false)
    val isRequestingReview: StateFlow<Boolean> = _isRequestingReview

    private val _reviewRequestMessage = MutableStateFlow("Tap the button to request review if eligible.")
    val reviewRequestMessage: StateFlow<String> = _reviewRequestMessage

    fun setExternalUserID(value: String) {
        _externalUserID.value = value
    }

    fun checkGrant() {
        viewModelScope.launch {
            _isCheckingGrant.value = true
            val trimmed = _externalUserID.value.trim()
            try {
                val result = Jishu.checkAccess(externalUserId = trimmed.ifEmpty { null })
                _isGranted.value = result.granted
                _grantCheckMessage.value = buildString {
                    appendLine("Granted: ${result.granted}")
                    append(
                        if (trimmed.isEmpty()) "Identity used: displayUserID ($userID)"
                        else "Identity used: externalUserId ($trimmed)"
                    )
                }
            } catch (e: Exception) {
                _isGranted.value = false
                _grantCheckMessage.value = "Grant check failed: ${e.message}"
            } finally {
                _isCheckingGrant.value = false
            }
        }
    }

    fun requestReviewIfEligible(activity: Activity) {
        viewModelScope.launch {
            _isRequestingReview.value = true
            val shown = Jishu.requestReviewIfEligible(activity)
            _isRequestingReview.value = false
            _reviewRequestMessage.value = if (shown) {
                "Review flow shown."
            } else {
                "Review flow not shown (not eligible right now)."
            }
        }
    }
}
