package io.auxilor.ecocrafting.custom.event

import com.willfp.eco.core.recipe.workstation.SmeltingRecipe
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

class CustomSmeltEvent(
    player: Player,
    val recipe: SmeltingRecipe,
    val item: ItemStack,
    val furnaceLocation: Location
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
