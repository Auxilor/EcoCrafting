package com.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.sound.PlayableSound
import com.auxilor.ecocrafting.EcoCraftingPlugin

internal fun configSound(key: String): PlayableSound? =
    EcoCraftingPlugin.configYml.getSubsectionOrNull("sounds.$key")
        ?.let { PlayableSound.create(it) }
