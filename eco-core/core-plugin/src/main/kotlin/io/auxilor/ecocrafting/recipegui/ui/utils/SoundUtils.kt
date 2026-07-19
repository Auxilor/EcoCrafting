package io.auxilor.ecocrafting.recipegui.ui.utils

import com.willfp.eco.core.sound.PlayableSound
import io.auxilor.ecocrafting.EcoCraftingPlugin

internal fun configSound(plugin: EcoCraftingPlugin, key: String) =
    plugin.configYml.getSubsectionOrNull("sounds.$key")
        ?.let { PlayableSound.create(it) }
