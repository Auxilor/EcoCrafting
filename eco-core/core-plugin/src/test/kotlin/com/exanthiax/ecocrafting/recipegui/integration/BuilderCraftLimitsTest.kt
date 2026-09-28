package com.exanthiax.ecocrafting.recipegui.integration

import com.exanthiax.ecocrafting.limit.model.UNLIMITED
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BuilderCraftLimitsTest {

    @Test
    fun `no craft-limits block is written when both limits are unlimited`() {
        assertEquals(emptyList<String>(), craftLimitLines(UNLIMITED, UNLIMITED, ""))
    }

    @Test
    fun `only the limits that are set are written, at the given indent`() {
        assertEquals(listOf("craft-limits:", "  player: 5"), craftLimitLines(5, UNLIMITED, ""))
        assertEquals(
            listOf("    craft-limits:", "      player: 0", "      global: 100"),
            craftLimitLines(0, 100, "    ")
        )
    }

    @Test
    fun `limits are read back from a section, defaulting to unlimited`() {
        val yaml = YamlConfiguration().apply { loadFromString("craft-limits:\n  player: 3\n") }
        assertEquals(3 to UNLIMITED, readCraftLimits(yaml))
        assertEquals(UNLIMITED to UNLIMITED, readCraftLimits(YamlConfiguration()))
        assertEquals(UNLIMITED to UNLIMITED, readCraftLimits(null))
    }

    @Test
    fun `negative stored limits read back as unlimited`() {
        assertEquals(UNLIMITED to 7, readCraftLimits(YamlConfiguration().apply { loadFromString("craft-limits:\n  player: -4\n  global: 7\n") }))
    }

    @Test
    fun `chat input accepts a count or an unlimited keyword`() {
        assertEquals(12, parseLimitInput(" 12 "))
        assertEquals(0, parseLimitInput("0"))
        assertEquals(UNLIMITED, parseLimitInput("-1"))
        assertEquals(UNLIMITED, parseLimitInput("none"))
        assertEquals(UNLIMITED, parseLimitInput("Unlimited"))
        assertNull(parseLimitInput("-2"))
        assertNull(parseLimitInput("lots"))
    }
}
