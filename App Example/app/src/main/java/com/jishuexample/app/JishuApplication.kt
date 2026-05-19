package com.jishuexample.app

import android.app.Application
import io.jishu.sdk.Jishu
import io.jishu.sdk.JishuDebugLevel

class JishuApplication : Application() {

    val reviewHandler = JishuReviewHandler()

    override fun onCreate() {
        super.onCreate()
        // Assign the custom UI handler before configure so it is ready for the first trackLaunch.
        Jishu.reviewUIHandler = reviewHandler
        Jishu.configure(
            context = this,
            server = ExampleAppConfig.server,
            apiToken = ExampleAppConfig.API_TOKEN,
            appId = ExampleAppConfig.APP_ID,
            debugLevel = JishuDebugLevel.VERBOSE
        )
    }
}
