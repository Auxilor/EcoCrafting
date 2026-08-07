package com.exanthiax.ecocrafting.recipegui.ui

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class RecipeCreatorChatListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeCreatorGUI: RecipeCreatorGUI
) : Listener {

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val handler = recipeCreatorGUI.awaitingInput.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())

        if (message.trim().equals("cancel", ignoreCase = true)) {
            plugin.server.scheduler.runTask(plugin, Runnable { recipeCreatorGUI.cancelSave(event.player) })
            return
        }

        plugin.server.scheduler.runTask(plugin, Runnable { handler(message) })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        recipeCreatorGUI.awaitingInput.remove(event.player.uniqueId)
        recipeCreatorGUI.pendingConfirm.remove(event.player.uniqueId)
    }
}
