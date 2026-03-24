package io.jishu.sdk.config

internal data class JishuConfig(
    val baseUrl: String,
    val apiToken: String,
    val appId: String,
    val environment: String?
) {
    init {
        val hostPart = baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
        require(!hostPart.contains('/')) {
            "baseUrl must be a root origin (e.g. https://jishu.page), not a path URL."
        }
    }
}
