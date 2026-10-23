package com.exanthiax.ecocrafting.libreforge

import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.filters.Filter
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.NamespacedKey

object FilterRecipe : Filter<NoCompileData, Collection<String>>("recipe") {
    override val description = "Matches when the crafted EcoCrafting recipe ID matches one of the given values."
    override val categories = setOf("crafting")
    override val valueType = ArgType.STRING_LIST
    override val additionalInfo = listOf(
        "Accepts the recipe ID (foo) or the full key (ecocrafting:foo). A stonecutter recipe also matches its own ID.",
        "Recipes from other plugins need the full key.",
        "Passes automatically when no recipe ID is present in the trigger data."
    )

    lateinit var recipeService: RecipeService

    override fun getValue(config: Config, data: TriggerData?, key: String): Collection<String> {
        return config.getStrings(key)
    }

    override fun isMet(data: TriggerData, value: Collection<String>, compileData: NoCompileData): Boolean {
        val text = data.text ?: return true
        val names = namesFor(text)

        return value.any { filter -> names.any { it.equals(filter, ignoreCase = true) } }
    }

    private fun namesFor(text: String): Set<String> {
        val key = NamespacedKey.fromString(text) ?: return setOf(text)

        if (key.namespace != "ecocrafting") {
            return setOf(text)
        }

        val names = mutableSetOf(text, key.key)
        val groupKey = recipeService.getMeta(recipeService.resolveBaseKey(key))?.groupKey

        if (groupKey != null) {
            names += groupKey.toString()
            names += groupKey.key
        }

        return names
    }
}
