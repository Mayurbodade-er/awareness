package com.awareness.core.domain.model

object DefaultMindfulnessMessages {
    val all: List<MindfulnessMessage> = listOf(
        MindfulnessMessage("serving", "Is this serving you?"),
        MindfulnessMessage("chose", "You chose this."),
        MindfulnessMessage("five_more", "Just five more?"),
        MindfulnessMessage("notice", "Notice how you feel."),
        MindfulnessMessage("intent", "Does this match your intent?"),
        MindfulnessMessage("breathe", "Take a conscious breath."),
        MindfulnessMessage("present", "Be present in this moment.")
    )

    val defaultSelectedIds: Set<String> = setOf("serving", "chose", "five_more")

    val fallback: List<MindfulnessMessage> =
        all.filter { it.id in defaultSelectedIds }
}
