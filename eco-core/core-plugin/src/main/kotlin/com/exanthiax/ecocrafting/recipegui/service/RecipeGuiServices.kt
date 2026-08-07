package com.exanthiax.ecocrafting.recipegui.service

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.quickcraft.service.QuickCraftService
import com.exanthiax.ecocrafting.recipe.model.ResolvedRecipe
import com.exanthiax.ecocrafting.recipe.service.RecipeResolverService
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.shop.service.ShopIntegrationService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import org.bukkit.entity.Player

// Parameter object bundling every collaborator the recipe-browsing GUIs need.
// Constructed once at the composition root and threaded explicitly through GUI
// constructors instead of each one taking five separate constructor parameters.
class RecipeGuiServices(
    val plugin: EcoCraftingPlugin,
    val recipeService: RecipeService,
    val resolverService: RecipeResolverService,
    val unlockService: RecipeUnlockService,
    val shopService: ShopIntegrationService
) {
    fun quickCraft(player: Player, recipe: ResolvedRecipe) =
        QuickCraftService(plugin, recipeService, resolverService, unlockService, player, recipe)
}
