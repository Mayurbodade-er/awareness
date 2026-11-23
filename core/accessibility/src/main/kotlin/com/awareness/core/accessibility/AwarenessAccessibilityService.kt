package com.awareness.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.awareness.core.domain.model.AwarenessEvent

/**
 * Sensor layer detecting target app foreground entry and exit.
 *
 * Invariants:
 * - Listens exclusively to TYPE_WINDOW_STATE_CHANGED.
 * - XML-level package filtering via accessibility_service_config.xml.
 * - Zero content scraping: accessibility node content and extras are never inspected.
 * - Zero polling / timers: purely reactive.
 */
class AwarenessAccessibilityService(
    private val eventBus: AccessibilityEventBus = AccessibilityEventBusImpl.instance,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) : AccessibilityService() {

    private var currentForegroundPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        handleWindowStateChanged(packageName)
    }

    /**
     * Internal state transition handler made visible for testing.
     */
    internal fun handleWindowStateChanged(packageName: String) {
        val isTarget = TargetAppAllowlist.isTargetApp(packageName)
        val previousPackage = currentForegroundPackage

        if (isTarget) {
            if (previousPackage != packageName) {
                val timestamp = timeProvider()
                // If previous package was another target, close its session first
                if (previousPackage != null && TargetAppAllowlist.isTargetApp(previousPackage)) {
                    eventBus.tryEmit(AwarenessEvent.SessionEnded(previousPackage, timestamp))
                }
                currentForegroundPackage = packageName
                eventBus.tryEmit(AwarenessEvent.SessionStarted(packageName, timestamp))
            }
        } else {
            if (previousPackage != null && TargetAppAllowlist.isTargetApp(previousPackage)) {
                val timestamp = timeProvider()
                eventBus.tryEmit(AwarenessEvent.SessionEnded(previousPackage, timestamp))
                currentForegroundPackage = null
            }
        }
    }

    override fun onInterrupt() {
        currentForegroundPackage?.let { pkg ->
            eventBus.tryEmit(AwarenessEvent.SessionEnded(pkg, timeProvider()))
            currentForegroundPackage = null
        }
    }

    override fun onDestroy() {
        currentForegroundPackage?.let { pkg ->
            eventBus.tryEmit(AwarenessEvent.SessionEnded(pkg, timeProvider()))
            currentForegroundPackage = null
        }
        super.onDestroy()
    }

    companion object {
        const val TAG = "AwarenessAccessibility"
    }
}
