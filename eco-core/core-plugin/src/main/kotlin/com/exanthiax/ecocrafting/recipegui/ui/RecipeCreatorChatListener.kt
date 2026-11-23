package com.exanthiax.ecocrafting.recipegui.ui

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import wiki.ecohub.core.plugin.startup.Prerequisite

class RecipeCreatorChatListener(
    private val plugin: EcoCraftingPlugin,
    private val recipeCreatorGUI: RecipeCreatorGUI
) : Listener {

    /**
     * Takes [message] as the answer [player] is being asked for.
     *
     * @return If the message was an answer, so the chat message should be cancelled.
     */
    fun handleInput(player: Player, message: String): Boolean {
        val handler = recipeCreatorGUI.awaitingInput.remove(player.uniqueId) ?: return false

        plugin.scheduler.on(player).run {
            if (message.trim().equals("cancel", ignoreCase = true)) {
                recipeCreatorGUI.cancelSave(player)
            } else {
                handler(message)
            }
        }
        return true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        recipeCreatorGUI.awaitingInput.remove(event.player.uniqueId)
        recipeCreatorGUI.pendingConfirm.remove(event.player.uniqueId)
    }

    /** The chat listener for this platform, feeding answers to [handleInput]. */
    fun chatListener(): Listener =
        if (Prerequisite.HAS_PAPER.isMet) {
            PaperRecipeCreatorChatListener(this)
        } else {
            SpigotRecipeCreatorChatListener(this)
        }
}

class SpigotRecipeCreatorChatListener(
    private val input: RecipeCreatorChatListener
) : Listener {
    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        if (input.handleInput(event.player, event.message)) {
            event.isCancelled = true
        }
    }
}
