package io.jishu.sdk

/**
 * Controls the verbosity of Jishu SDK Logcat output.
 */
enum class JishuDebugLevel {
    /** Prints only errors (❌) and retry attempts (🔄). This is the default. */
    DEFAULT,

    /** Prints all SDK activity: configuration, every request, response, formatted response body,
     *  retries, and errors. */
    VERBOSE
}
