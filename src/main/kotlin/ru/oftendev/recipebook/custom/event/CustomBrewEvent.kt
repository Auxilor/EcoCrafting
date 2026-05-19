package ru.oftendev.recipebook.custom.event

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.CustomRecipe

class CustomBrewEvent(
    player: Player,
    val recipe: CustomRecipe.Brewing,
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
