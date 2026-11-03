package com.netmonitor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.netmonitor.app.data.AppSettings
import com.netmonitor.app.service.SpeedService
import com.netmonitor.app.ui.NetMonitorApp
import com.netmonitor.app.ui.theme.NetMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onStop() {
        super.onStop()

        com.netmonitor.app.data.SpeedTester.clearResult()
    }

    private var currentTab by mutableStateOf(TAB_HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) com.netmonitor.app.data.UpdateChecker.promptDismissed = false
        AppSettings.init(this)
        enableEdgeToEdge()
        currentTab = intent?.getStringExtra(EXTRA_TAB) ?: TAB_HOME

        setContent {
            NetMonitorTheme {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { SpeedService.start(this) }

                LaunchedEffect(Unit) {
                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        SpeedService.start(this@MainActivity)
                    }
                }

                NetMonitorApp(
                    tab = currentTab,
                    onTabChange = { currentTab = it },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        SpeedService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_TAB)?.let { currentTab = it }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_HOME = "home"
        const val TAB_HISTORY = "history"
        const val TAB_SPEED = "speed"
        const val TAB_SETTINGS = "settings"
    }
}
