package com.exanthiax.ecocrafting.commands

/**
 * Decision logic for whether the in-game recipe builder is available.
 * Kept separate from the command classes so it's testable without Bukkit's
 * CommandSender/Player types.
 */
object PremiumGate {
    private const val BUILDER_MESSAGE = "§cThe in-game recipe builder is a premium-only feature."

    fun builderBlockMessage(freeVersion: Boolean): String? =
        if (freeVersion) BUILDER_MESSAGE else null
}
