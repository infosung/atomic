package com.infosung.atomic.starter.autoconfigure.heartbeat

import kotlin.test.Test
import kotlin.test.assertEquals

class AtomicHeartbeatPropertiesTest {
  @Test
  fun `leader ownerId default should be blank for per-instance lock ownership`() {
    val properties = AtomicHeartbeatProperties()
    assertEquals("", properties.dedup.leader.ownerId)
  }
}
