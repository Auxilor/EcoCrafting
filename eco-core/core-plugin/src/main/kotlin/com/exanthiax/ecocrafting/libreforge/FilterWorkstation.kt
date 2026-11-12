package com.exanthiax.ecocrafting.libreforge

import com.willfp.eco.core.blocks.matches
import wiki.ecohub.config.ArgType
import wiki.ecohub.config.Config
import wiki.ecohub.config.ViolationContext
import wiki.ecohub.game.block.Blocks
import wiki.ecohub.game.block.lookup.TestableBlock
import wiki.ecohub.rules.filter.Filter
import wiki.ecohub.rules.trigger.TriggerData
import wiki.ecohub.ui.text.containsIgnoreCase

object FilterWorkstation : Filter<Collection<TestableBlock>, Collection<String>>("workstation") {
    override val description = "Matches when the EcoCrafting recipe was crafted at one of the given workstation blocks."
    override val categories = setOf("crafting")
    override val valueType = ArgType.BLOCK_LIST
    override val additionalInfo = listOf("Passes automatically when no workstation block is present in the trigger data.")

    override fun getValue(config: Config, data: TriggerData?, key: String): Collection<String> {
        return config.getStrings(key)
    }

    override fun isMet(data: TriggerData, value: Collection<String>, compileData: Collection<TestableBlock>): Boolean {
        val block = data.block ?: return true
        return value.containsIgnoreCase(block.type.name)
                || compileData.matches(block)
    }

    override fun makeCompileData(
        config: Config, context: ViolationContext, values: Collection<String>
    ): Collection<TestableBlock> {
        return values.map { Blocks.lookup(it) }
    }
}
