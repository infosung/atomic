package com.infosung.atomic.contract.time

import java.time.Clock
import java.time.Instant
import java.util.TimeZone

class TimeProvider(
    private val defaultClock: Clock = Clock.systemUTC(),
    private val defaultTimeZone: TimeZone? = null,
) {
  @Volatile private var clock: Clock = defaultClock

  @Volatile private var timeZone: TimeZone? = defaultTimeZone

  fun nowInstant(): Instant = clock.instant()

  fun nowMillis(): Long = clock.millis()

  fun defaultTimeZone(): TimeZone = timeZone ?: TimeZone.getDefault()

  fun configureClock(clock: Clock) {
    this.clock = clock
  }

  fun configureTimeZone(timeZone: TimeZone) {
    this.timeZone = timeZone
  }

  fun clearConfiguredTimeZone() {
    this.timeZone = defaultTimeZone
  }

  fun reset() {
    this.clock = defaultClock
    this.timeZone = defaultTimeZone
  }
}
