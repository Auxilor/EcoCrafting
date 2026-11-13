package com.exanthiax.ecocrafting

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import wiki.ecohub.core.Eco
import wiki.ecohub.core.platform.ownsGlobal
import wiki.ecohub.core.plugin.EcoPlugin
import wiki.ecohub.core.plugin.startup.Prerequisite

internal inline fun Entity.runOwned(plugin: EcoPlugin, crossinline block: () -> Unit) {
    if (Eco.get().isOwnedByCurrentRegion(this)) {
        block()
    } else {
        plugin.scheduler.on(this).run { block() }
    }
}

internal inline fun EcoPlugin.runOnGlobalRegion(crossinline block: () -> Unit) {
    if (Prerequisite.HAS_FOLIA.isMet && !Bukkit.getServer().ownsGlobal()) {
        scheduler.global().run { block() }
    } else {
        block()
    }
}

internal fun Entity.isOwnedHere(): Boolean = Eco.get().isOwnedByCurrentRegion(this)
