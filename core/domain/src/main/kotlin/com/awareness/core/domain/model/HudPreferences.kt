package com.awareness.core.domain.model

data class HudPreferences(
    val initialRevealDelaySeconds: Int = 0,
    val messageDisplayDurationSeconds: Int = 30,
    val hideDurationSeconds: Int = 0,
    val selectedMessageIds: Set<String> = DefaultMindfulnessMessages.defaultSelectedIds,
    val customMessages: List<MindfulnessMessage> = emptyList(),
    val hudOpacityPercent: Int = 85,
    val hudScale: HudScale = HudScale.STANDARD
) {
    val revealDelayMillis: Long
        get() = initialRevealDelaySeconds * 1000L

    val allAvailableMessages: List<MindfulnessMessage>
        get() = DefaultMindfulnessMessages.all + customMessages

    val selectedMessages: List<MindfulnessMessage>
        get() {
            val filtered = allAvailableMessages.filter { it.id in selectedMessageIds }
            return if (filtered.isEmpty()) DefaultMindfulnessMessages.fallback else filtered
        }
}
