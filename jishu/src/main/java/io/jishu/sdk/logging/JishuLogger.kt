package io.jishu.sdk.logging

import android.util.Log
import io.jishu.sdk.JishuDebugLevel

internal object JishuLogger {
    private const val TAG = "JishuSDK"
    var level: JishuDebugLevel = JishuDebugLevel.DEFAULT

    /** Logs an error message. Always printed in both DEFAULT and VERBOSE modes. */
    fun error(message: String, throwable: Throwable? = null) {
        Log.e(TAG, "‼️ Jishu - $message", throwable)
    }

    /** Logs a verbose message. Only printed in VERBOSE mode. */
    fun verbose(message: String) {
        if (level == JishuDebugLevel.VERBOSE) {
            Log.d(TAG, "📱 Jishu - $message")
        }
    }
}
