package com.exanthiax.ecocrafting.libreforge

import com.exanthiax.ecocrafting.recipe.service.RecipeService
import org.bukkit.NamespacedKey
import wiki.ecohub.config.ArgType
import wiki.ecohub.config.Config
import wiki.ecohub.core.registry.NoCompileData
import wiki.ecohub.rules.filter.Filter
import wiki.ecohub.rules.trigger.TriggerData

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
        val key = NamespacedKey.fromString(text)?.takeIf { it.namespace == "ecocrafting" } ?: return setOf(text)
        val groupKey = recipeService.getMeta(recipeService.resolveBaseKey(key))?.groupKey

        return setOfNotNull(text, key.key, groupKey?.toString(), groupKey?.key)
    }
}
