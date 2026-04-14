package io.jishu.sdk.config

/**
 * Identifies which Jishu backend the SDK should connect to.
 */
enum class JishuEnvironment(internal val baseUrl: String) {
    /** The live production backend at `https://jishu.page`. Default. */
    PRODUCTION("https://jishu.page"),

    /** The staging backend at `https://staging.jishu.page`. */
    STAGING("https://staging.jishu.page");

    override fun toString(): String = name.lowercase()
}
