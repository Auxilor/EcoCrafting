package com.exanthiax.ecocrafting.libreforge

import com.willfp.eco.core.blocks.Blocks
import com.willfp.eco.core.blocks.TestableBlock
import com.willfp.eco.core.blocks.matches
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.util.containsIgnoreCase
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.filters.Filter
import com.willfp.libreforge.triggers.TriggerData

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
