package io.auxilor.ecocrafting.custom.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import org.bukkit.inventory.ItemStack
import io.auxilor.ecocrafting.recipe.RecipeDisplayType

/** Fired for cartography, grindstone, anvil, and villager recipes. */
class CustomWorkbenchCraftEvent(
    player: Player,
    val recipe: WorkstationRecipe,
    val item: ItemStack,
    val stationType: RecipeDisplayType
) : PlayerEvent(player), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLER_LIST
    companion object {
        private val HANDLER_LIST = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLER_LIST
    }
}
