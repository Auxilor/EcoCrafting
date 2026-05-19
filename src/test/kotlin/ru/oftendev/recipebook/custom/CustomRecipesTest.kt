package ru.oftendev.recipebook.custom

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomRecipesTest {

    @BeforeTest
    fun setup() {
        CustomRecipes.clear()
    }

    @Test
    fun `clear removes all entries`() {
        assertEquals(0, CustomRecipes.all().size)
    }

    @Test
    fun `all returns registered recipes`() {
        assertEquals(0, CustomRecipes.all().size)
    }
}
