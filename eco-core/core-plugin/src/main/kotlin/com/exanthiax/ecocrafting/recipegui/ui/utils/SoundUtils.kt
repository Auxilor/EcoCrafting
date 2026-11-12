package com.exanthiax.ecocrafting.recipegui.ui.utils

import com.exanthiax.ecocrafting.EcoCraftingPlugin
import wiki.ecohub.game.sound.PlayableSound

internal fun configSound(plugin: EcoCraftingPlugin, key: String) =
    plugin.configYml.getSubsectionOrNull("sounds.$key")
        ?.let { PlayableSound.create(it) }
