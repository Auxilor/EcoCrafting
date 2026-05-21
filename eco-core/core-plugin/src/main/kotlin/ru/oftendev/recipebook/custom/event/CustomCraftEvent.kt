package ru.oftendev.recipebook.custom.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import com.willfp.eco.core.recipe.workstation.WorkstationRecipe
import org.bukkit.inventory.ItemStack

class CustomCraftEvent(
    player: Player,
    val recipe: WorkstationRecipe,
    val item: ItemStack,
    val amount: Int
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
