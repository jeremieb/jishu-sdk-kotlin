package com.jishuexample.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.jishuexample.app.ui.theme.AppExampleTheme
import io.jishu.sdk.Jishu
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            Jishu.trackLaunch(this@MainActivity)
        }

        setContent {
            AppExampleTheme {
                MainScreen(activity = this)
            }
        }
    }
}
