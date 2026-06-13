package io.auxilor.ecocrafting.gui.utils

import com.willfp.eco.core.sound.PlayableSound
import io.auxilor.ecocrafting.plugin

internal fun configSound(key: String): PlayableSound? =
    plugin.configYml.getSubsectionOrNull("sounds.$key")
        ?.let { PlayableSound.create(it) }
