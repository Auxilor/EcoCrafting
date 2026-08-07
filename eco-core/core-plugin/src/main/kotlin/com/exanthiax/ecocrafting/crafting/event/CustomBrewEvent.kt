package com.exanthiax.ecocrafting.crafting.event

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
