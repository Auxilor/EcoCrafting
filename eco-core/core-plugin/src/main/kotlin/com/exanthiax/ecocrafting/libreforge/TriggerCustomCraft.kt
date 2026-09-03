package com.exanthiax.ecocrafting.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

// Deliberately not `craft`: libreforge already owns that ID and backs it with its own
// CraftItemEvent listener, so registering over it would silently kill the vanilla craft
// trigger server-wide.
object TriggerCustomCraft : Trigger("custom_craft") {
    override val description = "Fires when the player crafts an EcoCrafting custom recipe."

    override val categories = setOf("crafting")

    override val additionalInfo = listOf("Fires for every EcoCrafting workstation, not just the crafting table.")

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE,
        TriggerParameter.TEXT,
        TriggerParameter.BLOCK
    )
}
