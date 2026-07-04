package com.traffko.outlanderhub.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.traffko.outlanderhub.OutlanderApp
import com.traffko.outlanderhub.R
import com.traffko.outlanderhub.ui.theme.OutlanderHubTheme
import kotlin.math.roundToInt

private const val TAG = "OverlayService"

/**
 * Foreground service that keeps the floating vehicle-status pill on screen
 * over other apps — most usefully over the head unit's CarPlay/ZLink
 * projection, which otherwise hides all vehicle data. The pill hides itself
 * while Outlander Hub is foreground (the full UI is better) and reappears
 * when another app takes over.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var viewOwner: OverlayViewOwner? = null

    // Our own UI already shows everything — only float over other apps.
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            overlayView?.isVisible = false
        }

        override fun onStop(owner: LifecycleOwner) {
            overlayView?.isVisible = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; stopping")
            stopSelf()
            return
        }
        addOverlay()
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "removeView failed", it) }
        }
        overlayView = null
        viewOwner?.detach()
        viewOwner = null
        super.onDestroy()
    }

    private fun addOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: touches outside the pill go straight through to
            // the app underneath (CarPlay stays fully usable).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 24
        }
        layoutParams = params

        val owner = OverlayViewOwner().also { viewOwner = it }
        owner.attach()
        val view = ComposeView(this).apply {
            // A ComposeView outside an Activity needs its own view-tree owners.
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                OutlanderHubTheme {
                    OverlayContent(
                        stateFlow = (application as OutlanderApp).vehicleRepository.state,
                        onDrag = ::moveBy,
                    )
                }
            }
            isVisible = !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        windowManager.addView(view, params)
        overlayView = view
    }

    private fun moveBy(dx: Float, dy: Float) {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        windowManager.updateViewLayout(view, params)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.overlay_notification_title))
        .setOngoing(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}

/**
 * Minimal lifecycle/saved-state owner for a window that lives outside any
 * Activity. Compose requires both on the view tree.
 */
private class OverlayViewOwner : SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun attach() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun detach() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
