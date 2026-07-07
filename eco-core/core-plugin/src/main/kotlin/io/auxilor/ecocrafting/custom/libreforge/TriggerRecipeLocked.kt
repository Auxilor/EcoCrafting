package io.auxilor.ecocrafting.custom.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerRecipeLocked : Trigger("recipe_locked") {
    override val description = "Fires when a custom recipe is locked for the player."

    override val categories = setOf("crafting")

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.TEXT
    )
}
