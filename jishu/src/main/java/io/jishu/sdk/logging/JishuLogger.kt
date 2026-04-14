package io.jishu.sdk.logging

import android.util.Log
import io.jishu.sdk.JishuDebugLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal object JishuLogger {
    private const val TAG = "JishuSDK"
    var level: JishuDebugLevel = JishuDebugLevel.DEFAULT

    private val prettyJson = Json { prettyPrint = true }

    /** ❌  Always printed — error or unexpected condition. */
    fun error(message: String, throwable: Throwable? = null) {
        runCatching {
            Log.e(TAG, "❌ [Jishu] $message", throwable)
        }.getOrElse {
            System.err.println("❌ [Jishu] $message")
            throwable?.printStackTrace(System.err)
        }
    }

    /** 🔄  Always printed — retry attempt. */
    fun retry(message: String) {
        runCatching {
            Log.w(TAG, "🔄 [Jishu] $message")
        }.getOrElse {
            println("🔄 [Jishu] $message")
        }
    }

    /** 🚀  Verbose only — outgoing network request. */
    fun request(method: String, url: String) {
        if (level != JishuDebugLevel.VERBOSE) return
        runCatching {
            Log.d(TAG, "🚀 [Jishu] $method $url")
        }.getOrElse {
            println("🚀 [Jishu] $method $url")
        }
    }

    /** ✅ / ⚠️  Verbose only — received HTTP response. */
    fun response(status: Int, method: String, url: String) {
        if (level != JishuDebugLevel.VERBOSE) return
        val emoji = if (status in 200..299) "✅" else "⚠️"
        runCatching {
            Log.d(TAG, "$emoji [Jishu] $status $method $url")
        }.getOrElse {
            println("$emoji [Jishu] $status $method $url")
        }
    }

    /** ⚙️  Verbose only — SDK configuration log. */
    fun configure(message: String) {
        if (level != JishuDebugLevel.VERBOSE) return
        runCatching {
            Log.d(TAG, "⚙️ [Jishu] $message")
        }.getOrElse {
            println("⚙️ [Jishu] $message")
        }
    }

    /** 💬  Verbose only — general informational message. */
    fun info(message: String) {
        if (level != JishuDebugLevel.VERBOSE) return
        runCatching {
            Log.d(TAG, "💬 [Jishu] $message")
        }.getOrElse {
            println("💬 [Jishu] $message")
        }
    }

    /** 📦  Verbose only — pretty-printed JSON response body. */
    fun responseBody(body: String?) {
        if (level != JishuDebugLevel.VERBOSE) return
        if (body.isNullOrEmpty()) return
        val pretty = try {
            val element = Json.parseToJsonElement(body)
            prettyJson.encodeToString(JsonElement.serializer(), element)
        } catch (_: Exception) {
            body
        }
        runCatching {
            Log.d(TAG, "📦 [Jishu]\n$pretty")
        }.getOrElse {
            println("📦 [Jishu]\n$pretty")
        }
    }
}
