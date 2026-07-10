package io.auxilor.ecocrafting.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerGhostCraft : Trigger("ghost_craft") {
    override val description = "Fires when the player crafts a custom recipe that does not give the result item."

    override val categories = setOf("crafting")

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE
    )
}
