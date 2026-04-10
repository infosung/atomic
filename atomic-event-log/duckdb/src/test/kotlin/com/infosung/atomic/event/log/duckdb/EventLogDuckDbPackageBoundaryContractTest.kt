package com.infosung.atomic.event.log.duckdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventLogDuckDbPackageBoundaryContractTest {
  @Test
  fun `duckdb legal topology should export adapter out package`() {
    assertEquals(
        "com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbSqlRenderer",
        requiredClass("com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbSqlRenderer")
            .name,
    )
  }

  @Test
  fun `legacy duckdb root types should be removed`() {
    assertMissing("com.infosung.atomic.event.log.duckdb.EventLogDuckDbSqlRenderer")
  }

  private fun requiredClass(name: String): Class<*> = Class.forName(name)

  private fun assertMissing(name: String) {
    assertFailsWith<ClassNotFoundException> { Class.forName(name) }
  }
}
