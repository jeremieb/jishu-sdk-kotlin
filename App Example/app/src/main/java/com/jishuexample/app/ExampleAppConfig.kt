package com.jishuexample.app

import io.jishu.sdk.config.JishuEnvironment

object ExampleAppConfig {
    val server: JishuEnvironment = JishuEnvironment.PRODUCTION
    // ‼️ Replace these with your own credentials from your Jishu dashboard.
    const val API_TOKEN = "c55599b418f7d86fd1e5c1b5013c565e7ae8f4ebd7d2a156e33afbfcc705579f"
    const val APP_ID = "8dcd632afd0d7e5669e500e997b26124"
}
