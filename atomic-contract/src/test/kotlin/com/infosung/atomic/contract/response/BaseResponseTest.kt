package com.infosung.atomic.contract.response

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseResponseTest {
  @Test
  fun `ok should build successful response`() {
    val response = BaseResponse.ok<Map<String, String>>(data = mapOf("name" to "totp"))

    assertEquals("OK", response.code)
    assertEquals("Success", response.message)
    assertEquals("totp", response.data?.get("name"))
  }

  @Test
  fun `error should use exception metadata`() {
    val response = BaseResponse.error<Any>(IllegalStateException("boom"))

    assertEquals("IllegalStateException", response.code)
    assertEquals("boom", response.message)
  }

  @Test
  fun `page build should calculate hasNext`() {
    val page =
        OffsetPage.build(
            list = listOf(1, 2),
            totalSize = 5,
            currentPage = 1,
            size = 2,
        )

    assertEquals(2, page.list.size)
    assertTrue(page.hasNext)
    assertEquals(1, page.currentPage)
    assertEquals(2, page.size)
  }

  @Test
  fun `empty page should have no next`() {
    val page = OffsetPage.empty<Int>()

    assertEquals(0, page.totalSize)
    assertTrue(page.list.isEmpty())
    assertFalse(page.hasNext)
  }

  @Test
  fun `page build should set hasNext false when current page reaches total size`() {
    val page =
        OffsetPage.build(
            list = listOf(1, 2),
            totalSize = 4,
            currentPage = 1,
            size = 2,
        )

    assertFalse(page.hasNext)
  }
}
