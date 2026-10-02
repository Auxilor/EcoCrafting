package com.exanthiax.ecocrafting.limit.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CraftLimitPlaceholdersTest {

    @Test
    fun `simple recipe ids resolve every stat`() {
        assertEquals("sword" to LimitStat.USED, parseLimitPlaceholder("limit_sword_used"))
        assertEquals("sword" to LimitStat.REMAINING, parseLimitPlaceholder("limit_sword_remaining"))
        assertEquals("sword" to LimitStat.MAX, parseLimitPlaceholder("limit_sword_max"))
        assertEquals("sword" to LimitStat.GLOBAL_USED, parseLimitPlaceholder("limit_sword_global_used"))
        assertEquals("sword" to LimitStat.GLOBAL_REMAINING, parseLimitPlaceholder("limit_sword_global_remaining"))
        assertEquals("sword" to LimitStat.GLOBAL_MAX, parseLimitPlaceholder("limit_sword_global_max"))
    }

    @Test
    fun `recipe ids with underscores keep their full id`() {
        assertEquals("iron_sword" to LimitStat.GLOBAL_USED, parseLimitPlaceholder("limit_iron_sword_global_used"))
        assertEquals("iron_sword_2" to LimitStat.USED, parseLimitPlaceholder("limit_iron_sword_2_used"))
    }

    @Test
    fun `unrelated or incomplete args do not match`() {
        assertNull(parseLimitPlaceholder("limit_sword"))
        assertNull(parseLimitPlaceholder("limit__used"))
        assertNull(parseLimitPlaceholder("other_sword_used"))
    }

    @Test
    fun `an id ending in _global resolves to the recipe that exists`() {
        assertEquals("foo_global" to LimitStat.USED, parseLimitPlaceholder("limit_foo_global_used") { it == "foo_global" })
        assertEquals("foo" to LimitStat.GLOBAL_USED, parseLimitPlaceholder("limit_foo_global_used") { it == "foo" })
    }
}
