package com.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.sound.PlayableSound
import com.auxilor.ecocrafting.ecoCraftingPlugin

internal fun configSound(key: String): PlayableSound? =
    ecoCraftingPlugin.configYml.getSubsectionOrNull("sounds.$key")
        ?.let { PlayableSound.create(it) }
