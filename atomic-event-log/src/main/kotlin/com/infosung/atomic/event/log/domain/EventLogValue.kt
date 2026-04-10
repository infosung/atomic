package com.infosung.atomic.event.log.domain

import java.io.Serializable
import java.math.BigDecimal

/** Primitive-only payload value used by platform and business payloads. */
sealed interface EventLogValue : Serializable {
  data class Text(
      val value: String,
  ) : EventLogValue

  data class Integer(
      val value: Long,
  ) : EventLogValue {
    constructor(value: Int) : this(value.toLong())
  }

  data class Decimal(
      val value: BigDecimal,
  ) : EventLogValue {
    constructor(value: String) : this(BigDecimal(value))

    constructor(value: Double) : this(BigDecimal.valueOf(value))
  }

  data class Flag(
      val value: Boolean,
  ) : EventLogValue
}
