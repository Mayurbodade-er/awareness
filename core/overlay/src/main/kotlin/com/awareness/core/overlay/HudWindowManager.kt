package com.awareness.core.overlay

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

interface HudWindowManager {
    fun attach(view: View, params: HudWindowParams = HudWindowParams()): Result<Unit>
    fun remove(): Result<Unit>
    val isAttached: Boolean
}

class HudWindowManagerImpl(
    private val context: Context,
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
    private val permissionChecker: (Context) -> Boolean = { OverlayPermissionChecker.canDrawOverlays(it) }
) : HudWindowManager {

    private var currentView: View? = null
    override val isAttached: Boolean get() = currentView != null

    override fun attach(view: View, params: HudWindowParams): Result<Unit> {
        if (!permissionChecker(context)) {
            return Result.failure(SecurityException("SYSTEM_ALERT_WINDOW permission not granted"))
        }

        if (currentView != null) {
            remove()
        }

        return runCatching {
            // Inject lifecycle and state owners required by ComposeView inside WindowManager
            val hostOwner = OverlayHostLifecycleOwner()
            hostOwner.performRestore(null)
            hostOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            hostOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            hostOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            view.setViewTreeLifecycleOwner(hostOwner)
            view.setViewTreeSavedStateRegistryOwner(hostOwner)
            view.setViewTreeViewModelStoreOwner(hostOwner)

            val layoutParams = HudLayoutParamsFactory.create(context, params)
            windowManager.addView(view, layoutParams)
            currentView = view
        }
    }

    override fun remove(): Result<Unit> {
        val view = currentView ?: return Result.success(Unit)
        return runCatching {
            windowManager.removeView(view)
            currentView = null
        }
    }

    private class OverlayHostLifecycleOwner :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        fun performRestore(savedState: Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }
    }
}
