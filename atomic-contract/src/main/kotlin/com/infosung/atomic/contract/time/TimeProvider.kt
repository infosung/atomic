package com.infosung.atomic.contract.time

import java.time.Clock
import java.time.Instant
import java.util.TimeZone

/**
 * Centralized clock/time-zone provider for deterministic time handling.
 */
class TimeProvider(
    private val defaultClock: Clock = Clock.systemUTC(),
    private val defaultTimeZone: TimeZone? = null,
) {
  @Volatile private var clock: Clock = defaultClock

  @Volatile private var timeZone: TimeZone? = defaultTimeZone

  /**
   * Returns current instant from configured clock.
   */
  fun nowInstant(): Instant = clock.instant()

  /**
   * Returns current epoch millis from configured clock.
   */
  fun nowMillis(): Long = clock.millis()

  /**
   * Returns configured default time zone or JVM default when not configured.
   */
  fun defaultTimeZone(): TimeZone = timeZone ?: TimeZone.getDefault()

  /**
   * Replaces the active clock.
   */
  fun configureClock(clock: Clock) {
    this.clock = clock
  }

  /**
   * Replaces the active time zone.
   */
  fun configureTimeZone(timeZone: TimeZone) {
    this.timeZone = timeZone
  }

  /**
   * Clears configured time zone to constructor default.
   */
  fun clearConfiguredTimeZone() {
    this.timeZone = defaultTimeZone
  }

  /**
   * Resets clock and time zone to constructor defaults.
   */
  fun reset() {
    this.clock = defaultClock
    this.timeZone = defaultTimeZone
  }
}
