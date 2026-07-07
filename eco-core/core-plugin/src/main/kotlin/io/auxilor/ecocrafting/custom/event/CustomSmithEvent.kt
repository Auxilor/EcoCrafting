package io.auxilor.ecocrafting.custom.event

import com.willfp.eco.core.recipe.workstation.SmithingRecipe
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

class CustomSmithEvent(
    player: Player,
    val recipe: SmithingRecipe,
    val item: ItemStack
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
