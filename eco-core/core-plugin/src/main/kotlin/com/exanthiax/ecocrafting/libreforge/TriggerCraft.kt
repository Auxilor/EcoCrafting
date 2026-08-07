package com.exanthiax.ecocrafting.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerCraft : Trigger("craft") {
    override val description = "Fires when the player crafts an item."

    override val categories = setOf("crafting")

    override val additionalInfo = listOf("Supports EcoCrafting custom recipes.")

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE,
        TriggerParameter.TEXT,
        TriggerParameter.BLOCK
    )
}
