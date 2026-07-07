package io.auxilor.ecocrafting.gui

import io.auxilor.ecocrafting.plugin
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

object RecipeCreatorChatListener : Listener {

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val handler = RecipeCreatorGUI.awaitingInput.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())

        if (message.trim().equals("cancel", ignoreCase = true)) {
            plugin.server.scheduler.runTask(plugin, Runnable { RecipeCreatorGUI.cancelSave(event.player) })
            return
        }

        plugin.server.scheduler.runTask(plugin, Runnable { handler(message) })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        RecipeCreatorGUI.awaitingInput.remove(event.player.uniqueId)
    }
}
