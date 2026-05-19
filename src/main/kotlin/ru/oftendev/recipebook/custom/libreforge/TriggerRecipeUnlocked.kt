package ru.oftendev.recipebook.custom.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerRecipeUnlocked : Trigger("recipe_unlocked") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.TEXT
    )
}
