package com.example.navcarstereo.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTest {

    private val items = (1..10).toList()

    @Test
    fun `max page size returns everything`() {
        assertEquals(items, items.page(0, Int.MAX_VALUE))
    }

    @Test
    fun `slices pages and clamps the last one`() {
        assertEquals(listOf(1, 2, 3, 4), items.page(0, 4))
        assertEquals(listOf(9, 10), items.page(2, 4))
        assertEquals(emptyList<Int>(), items.page(3, 4))
    }
}
