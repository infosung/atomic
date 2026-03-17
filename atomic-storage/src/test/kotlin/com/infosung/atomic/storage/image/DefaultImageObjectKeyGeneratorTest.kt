package com.infosung.atomic.storage.image

import com.infosung.atomic.contract.time.TimeProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultImageObjectKeyGeneratorTest {
  @Test
  fun `generate should sanitize filename and include utc date path`() {
    val fixedClock = Clock.fixed(Instant.parse("2026-02-25T10:00:00Z"), ZoneOffset.UTC)
    val timeProvider = TimeProvider(defaultClock = fixedClock)
    val generator =
        DefaultImageObjectKeyGenerator(
            timeProvider = timeProvider,
            randomSuffixGenerator = { "fixedsuffix" },
        )

    val key = generator.generate("../hello world?.png")

    assertTrue(key.startsWith("images/2026/02/25/10/"))
    assertTrue(key.endsWith("_fixedsuffix_hello_world_.png"))
  }

  @Test
  fun `generate should use fallback image filename when sanitized name is blank`() {
    val fixedClock = Clock.fixed(Instant.parse("2026-02-25T10:00:00Z"), ZoneOffset.UTC)
    val timeProvider = TimeProvider(defaultClock = fixedClock)
    val generator =
        DefaultImageObjectKeyGenerator(
            timeProvider = timeProvider,
            randomSuffixGenerator = { "fixedsuffix" },
        )

    val key = generator.generate("///")

    assertTrue(key.startsWith("images/2026/02/25/10/"))
    assertTrue(key.endsWith("_fixedsuffix_image"))
  }

  @Test
  fun `generate should support custom base prefix`() {
    val fixedClock = Clock.fixed(Instant.parse("2026-02-25T10:00:00Z"), ZoneOffset.UTC)
    val timeProvider = TimeProvider(defaultClock = fixedClock)
    val generator =
        DefaultImageObjectKeyGenerator(
            timeProvider = timeProvider,
            basePrefix = "uploads",
            randomSuffixGenerator = { "fixedsuffix" },
        )

    val key = generator.generate("sample.png")

    assertTrue(key.startsWith("uploads/2026/02/25/10/"))
    assertTrue(key.endsWith("_fixedsuffix_sample.png"))
  }

  @Test
  fun `generate should bound long filenames while preserving extension`() {
    val fixedClock = Clock.fixed(Instant.parse("2026-02-25T10:00:00Z"), ZoneOffset.UTC)
    val timeProvider = TimeProvider(defaultClock = fixedClock)
    val generator =
        DefaultImageObjectKeyGenerator(
            timeProvider = timeProvider,
            randomSuffixGenerator = { "fixedsuffix" },
        )

    val key = generator.generate("${"a".repeat(800)}.png")

    assertTrue(key.startsWith("images/2026/02/25/10/"))
    assertTrue(key.endsWith(".png"))
    assertTrue(key.length <= 512)
    assertEquals(512, key.length)
  }
}
