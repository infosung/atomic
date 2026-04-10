package com.infosung.atomic.event.log.domain

import java.io.Serializable

/** Platform-specific reserved payload contract. */
interface EventLogPlatformPayload : Serializable {
  val platform: EventLogPlatform

  fun toFields(): Map<String, EventLogValue>

  fun validate(policy: EventLogPolicy): String?
}
