package com.awareness.feature.hud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.awareness.core.domain.model.HudState
import com.awareness.core.overlay.HudWindowManager
import com.awareness.core.overlay.HudWindowManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Passive Foreground Service hosting WindowManager and ComposeView for HUD overlay.
 *
 * Invariants:
 * - specialUse Foreground Service on API 34+ with subtype 'mindfulness_hud'.
 * - Wrapped startForeground in try-catch for ForegroundServiceStartNotAllowedException.
 * - Passive renderer: consumes HudState; zero business logic; never accesses DataStore or accessibility tree directly.
 */
class HudForegroundService(
    private val windowManagerProvider: (Context) -> HudWindowManager = { HudWindowManagerImpl(it) }
) : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hudWindowManager: HudWindowManager? = null
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.e("HudService", "onStartCommand: action=${intent?.action}, startId=$startId")
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundSafely()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundSafely()
                attachOverlayIfNeeded()
            }
        }
        return START_STICKY
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            android.util.Log.e("HudService", "startForeground succeeded")
        } catch (e: Exception) {
            android.util.Log.e("HudService", "startForeground failed", e)
            stopSelf()
        }
    }

    private fun attachOverlayIfNeeded() {
        if (hudWindowManager == null) {
            hudWindowManager = windowManagerProvider(this)
        }

        if (composeView == null) {
            val view = ComposeView(this).apply {
                setContent {
                    HudOverlayView(hudStateFlow = hudStateFlowProvider())
                }
            }
            composeView = view
            val result = hudWindowManager?.attach(view)
            android.util.Log.e("HudService", "hudWindowManager.attach result: $result")
        }
    }

    private fun stopForegroundSafely() {
        android.util.Log.e("HudService", "stopForegroundSafely called")
        hudWindowManager?.remove()
        hudWindowManager = null
        composeView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        android.util.Log.e("HudService", "onTimeout called! startId=$startId, fgsType=$fgsType")
        stopForegroundSafely()
        stopSelf()
    }

    override fun onDestroy() {
        android.util.Log.e("HudService", "onDestroy called")
        stopForegroundSafely()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mindfulness HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors device mindfulness overlay"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Awareness Active")
            .setContentText("Mindfulness HUD running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.awareness.app.hud.START"
        const val ACTION_STOP = "com.awareness.app.hud.STOP"
        const val CHANNEL_ID = "hud_service_channel"
        const val NOTIFICATION_ID = 2001

        val defaultHudStateFlow = MutableStateFlow(
            HudState(
                isVisible = false,
                message = "",
                elapsedMillis = 0L,
                reelCount = 0,
                showReelCount = false,
                browserDomainLabel = null
            )
        )

        var hudStateFlowProvider: () -> StateFlow<HudState> = { defaultHudStateFlow }

        fun start(context: Context) {
            val intent = Intent(context, HudForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HudForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
