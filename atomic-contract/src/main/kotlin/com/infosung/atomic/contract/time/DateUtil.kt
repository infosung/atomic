package com.infosung.atomic.contract.time

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Date arithmetic helpers.
 */
object DateUtil {
  /**
   * Adds time amount to [date] using [Calendar.add] semantics.
   *
   * @param date Base date.
   * @param field Calendar field constant (for example [Calendar.DAY_OF_MONTH]).
   * @param time Amount to add (negative for subtraction).
   * @param timeZone Time zone used for calendar calculation.
   */
  fun plusTime(
      date: Date,
      field: Int,
      time: Int,
      timeZone: TimeZone = TimeZone.getDefault(),
  ): Date {
    val calendar = Calendar.getInstance(timeZone)
    calendar.time = date
    calendar.add(field, time)
    return calendar.time
  }
}
