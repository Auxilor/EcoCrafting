package com.exanthiax.ecocrafting.recipegui.ui.utils

import com.willfp.eco.core.sound.PlayableSound
import com.exanthiax.ecocrafting.EcoCraftingPlugin

internal fun configSound(plugin: EcoCraftingPlugin, key: String) =
    plugin.configYml.getSubsectionOrNull("sounds.$key")
        ?.let { PlayableSound.create(it) }
