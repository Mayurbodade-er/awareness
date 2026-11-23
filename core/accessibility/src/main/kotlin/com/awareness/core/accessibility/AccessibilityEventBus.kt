package com.awareness.core.accessibility

import com.awareness.core.domain.model.AwarenessEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event-driven communication bus emitting foreground transitions and reel scrolls.
 *
 * Buffer capacity is strictly 16 with BufferOverflow.DROP_OLDEST to ensure
 * zero memory leaks and non-blocking emission.
 */
interface AccessibilityEventBus {
    val events: SharedFlow<AwarenessEvent>
    fun tryEmit(event: AwarenessEvent): Boolean
}

class AccessibilityEventBusImpl(
    private val _events: MutableSharedFlow<AwarenessEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
) : AccessibilityEventBus {

    override val events: SharedFlow<AwarenessEvent> = _events.asSharedFlow()

    override fun tryEmit(event: AwarenessEvent): Boolean {
        return _events.tryEmit(event)
    }

    companion object {
        val instance: AccessibilityEventBus by lazy { AccessibilityEventBusImpl() }
    }
}
