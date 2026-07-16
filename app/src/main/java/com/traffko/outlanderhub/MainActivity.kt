package com.traffko.outlanderhub

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.traffko.outlanderhub.apps.ProjectionApps
import com.traffko.outlanderhub.ui.AppRoot
import com.traffko.outlanderhub.ui.theme.OutlanderHubTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            OutlanderHubTheme {
                AppRoot(viewModel)
            }
        }
        if (savedInstanceState == null) maybeAutoLaunchProjection()
    }

    /**
     * Optionally drop straight into the CarPlay/projection app when the unit
     * boots into the launcher (Setup → CarPlay → open on startup). Gated to a
     * window after boot: a HOME press mid-drive must land on the launcher,
     * not bounce back into CarPlay.
     */
    private fun maybeAutoLaunchProjection() {
        if (SystemClock.elapsedRealtime() > BOOT_AUTO_LAUNCH_WINDOW_MS) return
        lifecycleScope.launch {
            val settings = (application as OutlanderApp).settingsRepository.settings.first()
            if (!settings.projectionAutoLaunch) return@launch
            // PackageManager queries can block; keep them off the main thread
            // during startup.
            val intent = withContext(Dispatchers.IO) {
                ProjectionApps.resolve(packageManager, settings.projectionPackage)
                    ?.let { packageManager.getLaunchIntentForPackage(it) }
            } ?: return@launch
            // Never let a broken projection app crash-loop the launcher at boot.
            runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val BOOT_AUTO_LAUNCH_WINDOW_MS = 180_000L
    }
}
