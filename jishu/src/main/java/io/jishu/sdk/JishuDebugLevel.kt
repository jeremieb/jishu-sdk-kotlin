package io.jishu.sdk

/**
 * Controls the verbosity of Jishu SDK console output.
 */
enum class JishuDebugLevel {
    /** Prints only errors to Logcat, prefixed with "‼️ Jishu -". This is the default. */
    DEFAULT,

    /** Prints all SDK activity (requests, responses, retries) to Logcat, prefixed with "📱 Jishu -". */
    VERBOSE
}
