package com.exanthiax.ecocrafting.trade.service

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import com.exanthiax.ecocrafting.recipe.model.EcoCraftingMeta
import com.exanthiax.ecocrafting.recipe.service.RecipeService
import com.exanthiax.ecocrafting.unlock.service.RecipeUnlockService
import com.willfp.eco.core.gui.view.ViewBuilders
import com.willfp.eco.core.recipe.workstation.VillagerRecipe
import com.willfp.eco.util.formatEco
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.MerchantRecipe

// Builds and opens a virtual merchant from EcoCrafting villager recipes, so trades can be
// offered without a villager entity. The entity-side fields on a VillagerRecipe (profession,
// min-level, chance, wandering-trader, villager-xp) have no meaning here and are ignored.
class TradeMerchantFactory(
    private val plugin: EcoCraftingPlugin,
    private val recipeService: RecipeService,
    private val unlockService: RecipeUnlockService,
    private val sessionService: TradeSessionService
) {
    // Returns false when nothing the player is allowed to see survived the filter, in which
    // case no GUI is opened.
    fun open(player: Player, recipes: List<VillagerRecipe>): Boolean {
        val eligible = recipes.mapNotNull { recipe ->
            val meta = recipeService.getMeta(recipe.key) ?: return@mapNotNull null
            if (!isTradeVisible(player, recipe, meta, unlockService)) return@mapNotNull null
            val merchantRecipe = buildMerchantRecipe(recipe, meta) ?: return@mapNotNull null
            recipe.key to merchantRecipe
        }

        if (eligible.isEmpty()) return false

        val merchant = Bukkit.createMerchant()
        merchant.recipes = eligible.map { it.second }

        // Registered before opening: the click handler resolves trades through the session, and
        // opening the view can fire inventory events synchronously.
        sessionService.open(player, eligible.map { it.first })

        // eco's ViewBuilders, not MenuType.MERCHANT.builder() directly - the Bukkit builder takes
        // its title as a String on Spigot and a Component on Paper, so calling it here would tie
        // the plugin to whichever one we compiled against.
        ViewBuilders.merchant()
            .title(plugin.configYml.getString("trade-gui.title").formatEco())
            .merchant(merchant)
            .open(player)
        return true
    }

    private fun buildMerchantRecipe(recipe: VillagerRecipe, meta: EcoCraftingMeta): MerchantRecipe? {
        val result = recipe.output?.clone() ?: return null
        val input1 = (recipe.input1Display ?: recipe.input1.item)?.clone() ?: return null

        // uses = 0, no experience reward, no price multiplier - there's no villager to level up
        // or to apply demand-based pricing.
        val merchantRecipe = MerchantRecipe(result, 0, resolveMaxUses(meta.maxUses), false, 0, 0f)
        merchantRecipe.addIngredient(input1)
        recipe.input2?.let { secondInput ->
            val input2 = (recipe.input2Display ?: secondInput.item)?.clone() ?: return@let
            merchantRecipe.addIngredient(input2)
        }
        return merchantRecipe
    }
}
