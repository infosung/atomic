package com.infosung.atomic.event.log.spring.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventLogSpringWebPackageBoundaryContractTest {
  @Test
  fun `spring web legal topology should export adapter in web package`() {
    assertEquals(
        "com.infosung.atomic.event.log.spring.web.adapter.in.web.AtomicSpringWebEventLogSaver",
        requiredClass(
                "com.infosung.atomic.event.log.spring.web.adapter.in.web.AtomicSpringWebEventLogSaver")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.spring.web.adapter.in.web.AtomicSpringWebEventIdGenerator",
        requiredClass(
                "com.infosung.atomic.event.log.spring.web.adapter.in.web.AtomicSpringWebEventIdGenerator")
            .name,
    )
  }

  @Test
  fun `legacy spring web root types should be removed`() {
    assertMissing("com.infosung.atomic.event.log.spring.web.AtomicSpringWebEventLogSaver")
    assertMissing("com.infosung.atomic.event.log.spring.web.AtomicSpringWebEventIdGenerator")
  }

  private fun requiredClass(name: String): Class<*> = Class.forName(name)

  private fun assertMissing(name: String) {
    assertFailsWith<ClassNotFoundException> { Class.forName(name) }
  }
}
