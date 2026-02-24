package com.infosung.atomic.contract.time

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

object DateUtil {
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
