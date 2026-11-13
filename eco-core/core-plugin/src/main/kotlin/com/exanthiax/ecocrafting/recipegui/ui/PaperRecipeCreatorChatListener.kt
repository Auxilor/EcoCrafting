package com.exanthiax.ecocrafting.recipegui.ui

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/** Kept apart from [SpigotRecipeCreatorChatListener] so Spigot never loads [AsyncChatEvent]. */
class PaperRecipeCreatorChatListener(
    private val input: RecipeCreatorChatListener
) : Listener {
    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        if (input.handleInput(event.player, PlainTextComponentSerializer.plainText().serialize(event.message()))) {
            event.isCancelled = true
        }
    }
}
