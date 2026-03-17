package com.infosung.atomic.contract.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogStringPreviewTest {
  @Test
  fun `summarize should return null when value is null`() {
    assertNull(LogStringPreview.summarize(null))
  }

  @Test
  fun `summarize should keep short value unchanged`() {
    assertEquals("atomic", LogStringPreview.summarize("atomic"))
  }

  @Test
  fun `summarize should keep default boundary length unchanged`() {
    val value = "a".repeat(LogStringPreview.DEFAULT_MAX_LENGTH)

    assertEquals(value, LogStringPreview.summarize(value))
  }

  @Test
  fun `summarize should append ellipsis when value exceeds default boundary`() {
    val value = "b".repeat(LogStringPreview.DEFAULT_MAX_LENGTH + 1)

    assertEquals(
        "b".repeat(LogStringPreview.DEFAULT_MAX_LENGTH - 3) + "...",
        LogStringPreview.summarize(value),
    )
  }

  @Test
  fun `summarize should clip to requested max length`() {
    assertEquals("abcde...", LogStringPreview.summarize("abcdefghijk", maxLength = 8))
  }
}
