package com.exanthiax.ecocrafting.libreforge

import wiki.ecohub.rules.trigger.Trigger
import wiki.ecohub.rules.trigger.TriggerParameter

object TriggerRecipeUnlocked : Trigger("recipe_unlocked") {
    override val description = "Fires when a custom recipe is unlocked for the player."

    override val categories = setOf("crafting")

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.TEXT
    )
}
