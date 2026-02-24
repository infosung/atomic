package com.infosung.atomic.contract.response

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CursorPageTest {
  @Test
  fun `default cursor page should use empty list and default metadata`() {
    val page = CursorPage<Int>()

    assertEquals(emptyList(), page.list)
    assertFalse(page.hasNext)
    assertEquals(10, page.size)
    assertNull(page.cursor)
  }

  @Test
  fun `cursor page should preserve provided values`() {
    val page =
        CursorPage(
            list = listOf("a", "b"),
            hasNext = true,
            size = 2,
            cursor = "next-cursor",
        )

    assertEquals(listOf("a", "b"), page.list)
    assertEquals(true, page.hasNext)
    assertEquals(2, page.size)
    assertEquals("next-cursor", page.cursor)
  }
}
