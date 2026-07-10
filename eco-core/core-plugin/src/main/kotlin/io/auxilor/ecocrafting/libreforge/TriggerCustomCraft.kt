package io.auxilor.ecocrafting.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerCustomCraft : Trigger("custom_craft") {
    override val description = "Fires when the player crafts an item using a custom recipe."

    override val categories = setOf("crafting")

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE,
        TriggerParameter.TEXT
    )
}
