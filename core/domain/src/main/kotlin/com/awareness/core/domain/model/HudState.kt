package com.awareness.core.domain.model

data class HudState(
    val isVisible: Boolean,
    val message: String,
    val elapsedMillis: Long,
    val reelCount: Int,
    val showReelCount: Boolean,
    val browserDomainLabel: String? = null,
    val opacityPercent: Int = 85,
    val scale: HudScale = HudScale.STANDARD
)
