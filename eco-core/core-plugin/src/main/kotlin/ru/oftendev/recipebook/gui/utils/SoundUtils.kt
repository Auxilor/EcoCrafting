package ru.oftendev.recipebook.gui.utils

import com.willfp.eco.core.sound.PlayableSound
import ru.oftendev.recipebook.recipeBookPlugin

internal fun configSound(key: String): PlayableSound? =
    recipeBookPlugin.configYml.getSubsectionOrNull("sounds.$key")
        ?.let { PlayableSound.create(it) }
