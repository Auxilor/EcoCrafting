package io.auxilor.ecocrafting.custom.event

import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import io.auxilor.ecocrafting.recipe.RecipeDisplayType
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

/** Fired for cartography, grindstone, anvil, and villager recipes. */
class CustomWorkbenchCraftEvent(
    player: Player,
    val recipe: WorkstationRecipe,
    val item: ItemStack,
    val stationType: RecipeDisplayType
) : PlayerEvent(player), Cancellable {
    private var _cancelled = false

    override fun isCancelled() = _cancelled

    override fun setCancelled(cancel: Boolean) {
        _cancelled = cancel
    }

    override fun getHandlers(): HandlerList {
        return handlerList
    }

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlerList
        }
    }
}
