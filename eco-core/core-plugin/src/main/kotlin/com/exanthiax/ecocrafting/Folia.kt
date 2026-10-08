package com.exanthiax.ecocrafting

import com.willfp.eco.core.Eco
import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.Prerequisite
import org.bukkit.Bukkit
import org.bukkit.entity.Entity

internal inline fun Entity.runOwned(plugin: EcoPlugin, crossinline block: () -> Unit) {
    if (Eco.get().isOwnedByCurrentRegion(this)) {
        block()
    } else {
        plugin.scheduler.on(this).run { block() }
    }
}

internal inline fun EcoPlugin.runOnGlobalRegion(crossinline block: () -> Unit) {
    if (Prerequisite.HAS_FOLIA.isMet && !Bukkit.isGlobalTickThread()) {
        scheduler.global().run { block() }
    } else {
        block()
    }
}

internal fun Entity.isOwnedHere(): Boolean = Eco.get().isOwnedByCurrentRegion(this)
