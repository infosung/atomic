package com.infosung.atomic.contract.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach

class TimeProviderTest {
  private val timeProvider = TimeProvider()

  @AfterEach
  fun reset() {
    timeProvider.reset()
  }

  @Test
  fun `configureClock should affect now values`() {
    val instant = Instant.parse("2026-02-24T00:00:00Z")
    val fixed = Clock.fixed(instant, ZoneOffset.UTC)
    timeProvider.configureClock(fixed)

    assertEquals(instant.toEpochMilli(), timeProvider.nowMillis())
    assertEquals(instant, timeProvider.nowInstant())
  }

  @Test
  fun `configureTimeZone should affect default timezone`() {
    timeProvider.configureTimeZone(TimeZone.getTimeZone("Asia/Seoul"))

    assertEquals("Asia/Seoul", timeProvider.defaultTimeZone().id)
  }

  @Test
  fun `constructor defaults should be used and restored by reset`() {
    val instant = Instant.parse("2026-02-24T00:00:00Z")
    val provider =
        TimeProvider(
            defaultClock = Clock.fixed(instant, ZoneOffset.UTC),
            defaultTimeZone = TimeZone.getTimeZone("Asia/Seoul"),
        )

    assertEquals(instant, provider.nowInstant())
    assertEquals("Asia/Seoul", provider.defaultTimeZone().id)

    provider.configureClock(Clock.fixed(instant.plusSeconds(10), ZoneOffset.UTC))
    provider.configureTimeZone(TimeZone.getTimeZone("UTC"))
    provider.reset()

    assertEquals(instant, provider.nowInstant())
    assertEquals("Asia/Seoul", provider.defaultTimeZone().id)
  }
}
