package com.infosung.atomic.contract.time

import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach

class DateUtilTest {
  private val originalTimeZoneId: String = TimeZone.getDefault().id

  @AfterEach
  fun resetTimeZone() {
    TimeZone.setDefault(TimeZone.getTimeZone(originalTimeZoneId))
  }

  @Test
  fun `plusTime should add the requested calendar field`() {
    val base = Date(0L)
    val result = DateUtil.plusTime(base, Calendar.HOUR_OF_DAY, 1)

    assertEquals(3_600_000L, result.time)
  }

  @Test
  fun `plusTime should support negative offsets`() {
    val base = Date(86_400_000L)
    val result = DateUtil.plusTime(base, Calendar.DATE, -1)

    assertEquals(0L, result.time)
  }

  @Test
  fun `plusTime should allow explicit timezone configuration`() {
    val base = Date.from(Instant.parse("2024-03-09T12:00:00Z"))

    val utcResult = DateUtil.plusTime(base, Calendar.DATE, 1, TimeZone.getTimeZone("UTC"))
    val newYorkResult =
        DateUtil.plusTime(base, Calendar.DATE, 1, TimeZone.getTimeZone("America/New_York"))

    assertEquals(86_400_000L, utcResult.time - base.time)
    assertEquals(82_800_000L, newYorkResult.time - base.time)
  }

  @Test
  fun `plusTime should keep previous behavior using JVM default timezone when not configured`() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    val base = Date.from(Instant.parse("2024-03-09T12:00:00Z"))

    val result = DateUtil.plusTime(base, Calendar.DATE, 1)

    assertEquals(82_800_000L, result.time - base.time)
  }
}
