package com.jishuexample.app

import android.app.Application
import io.jishu.sdk.Jishu
import io.jishu.sdk.JishuDebugLevel

class JishuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Jishu.configure(
            context = this,
            server = ExampleAppConfig.server,
            apiToken = ExampleAppConfig.API_TOKEN,
            appId = ExampleAppConfig.APP_ID,
            debugLevel = JishuDebugLevel.VERBOSE
        )
    }
}
