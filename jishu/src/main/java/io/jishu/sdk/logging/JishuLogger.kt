package io.jishu.sdk.logging

import android.util.Log

internal object JishuLogger {
    private const val TAG = "JishuSDK"
    var debugEnabled = false

    fun d(message: String) {
        if (debugEnabled) Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
