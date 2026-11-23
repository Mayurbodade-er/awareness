package com.awareness.core.domain.model

data class HudPreferences(
    val initialRevealDelaySeconds: Int = 0,
    val messageDisplayDurationSeconds: Int = 30,
    val selectedMessageIds: Set<String> = DefaultMindfulnessMessages.defaultSelectedIds
) {
    val revealDelayMillis: Long
        get() = initialRevealDelaySeconds * 1000L

    val selectedMessages: List<MindfulnessMessage>
        get() {
            val filtered = DefaultMindfulnessMessages.all.filter { it.id in selectedMessageIds }
            return if (filtered.isEmpty()) DefaultMindfulnessMessages.fallback else filtered
        }
}
