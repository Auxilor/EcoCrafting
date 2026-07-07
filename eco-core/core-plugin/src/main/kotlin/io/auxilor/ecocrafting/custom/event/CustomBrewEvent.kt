package io.auxilor.ecocrafting.custom.event

import com.willfp.eco.core.recipe.workstation.BrewingRecipe
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

class CustomBrewEvent(
    player: Player,
    val recipe: BrewingRecipe,
    val item: ItemStack,
    val brewingLocation: Location,
    val bottlesAffected: Int
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
