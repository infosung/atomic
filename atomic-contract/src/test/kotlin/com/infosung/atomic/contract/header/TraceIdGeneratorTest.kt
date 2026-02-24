package com.infosung.atomic.contract.header

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach

class TraceIdGeneratorTest {
  private val traceIdGenerator = TraceIdGenerator()

  @AfterEach
  fun reset() {
    traceIdGenerator.reset()
  }

  @Test
  fun `configure should override generated trace id`() {
    traceIdGenerator.configure { "fixed-trace-id" }

    assertEquals("fixed-trace-id", traceIdGenerator.generate())
  }

  @Test
  fun `default generator should return non blank trace id`() {
    val traceId = traceIdGenerator.generate()

    assertTrue(traceId.isNotBlank())
  }

  @Test
  fun `constructor supplier should be used and restored by reset`() {
    val generator = TraceIdGenerator(defaultSupplier = { "constructor-default" })

    assertEquals("constructor-default", generator.generate())
    generator.configure { "override" }
    assertEquals("override", generator.generate())

    generator.reset()
    assertEquals("constructor-default", generator.generate())
  }
}
