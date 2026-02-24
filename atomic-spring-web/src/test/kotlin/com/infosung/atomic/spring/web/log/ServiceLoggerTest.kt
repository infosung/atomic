package com.infosung.atomic.spring.web.log

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServiceLoggerTest {
  @Test
  fun `logger should reject non positive queue size`() {
    assertThrows(IllegalArgumentException::class.java) {
      ServiceLogger(logSaver = FlakyLogSaver(), maxQueueSize = 0)
    }
  }

  @Test
  fun `failed send should be retried without losing previous logs`() {
    val saver = FlakyLogSaver()
    val logger = ServiceLogger(saver)

    logger.logging(requestLog("trace-1", "/v1/a"))
    logger.send()
    assertEquals(1, saver.callCount.get())
    assertEquals(0, saver.savedLogs.size)

    logger.logging(requestLog("trace-2", "/v1/b"))
    logger.send()

    assertEquals(2, saver.callCount.get())
    assertEquals(2, saver.savedLogs.size)
    assertEquals("trace-1", saver.savedLogs[0].traceId)
    assertEquals("trace-2", saver.savedLogs[1].traceId)
  }

  @Test
  fun `logs added while sending should be flushed by next send`() {
    val saver = BlockingLogSaver()
    val logger = ServiceLogger(saver)

    logger.logging(requestLog("trace-1", "/v1/a"))

    val firstSend = thread(start = true) { logger.send() }

    assertTrue(saver.started.await(1, TimeUnit.SECONDS))
    logger.logging(requestLog("trace-2", "/v1/b"))
    logger.send()

    saver.release.countDown()
    firstSend.join()
    logger.send()

    assertEquals(2, saver.savedLogs.size)
    assertTrue(saver.savedLogs.any { it.traceId == "trace-1" })
    assertTrue(saver.savedLogs.any { it.traceId == "trace-2" })
  }

  @Test
  fun `logger should drop oldest logs when queue is full`() {
    val saver =
        FlakyLogSaver().apply {
          callCount.set(1) // bypass failure path for this test
        }
    val logger = ServiceLogger(saver, maxQueueSize = 2)

    logger.logging(requestLog("trace-1", "/v1/a"))
    logger.logging(requestLog("trace-2", "/v1/b"))
    logger.logging(requestLog("trace-3", "/v1/c"))
    logger.send()

    assertEquals(2, saver.savedLogs.size)
    assertTrue(saver.savedLogs.none { it.traceId == "trace-1" })
    assertTrue(saver.savedLogs.any { it.traceId == "trace-2" })
    assertTrue(saver.savedLogs.any { it.traceId == "trace-3" })
  }

  private fun requestLog(
      traceId: String,
      endpoint: String,
  ): ServiceApiRequestLog =
      ServiceApiRequestLog(
          traceId = traceId,
          logTime = 1_000L,
          httpMethod = "GET",
          endPoint = endpoint,
      )

  private class FlakyLogSaver : LogSaver {
    val callCount = AtomicInteger(0)
    val savedLogs = mutableListOf<ServiceLog>()

    override fun saveAll(logs: List<ServiceLog>) {
      if (callCount.getAndIncrement() == 0) {
        throw IllegalStateException("first save failed")
      }
      savedLogs.addAll(logs)
    }
  }

  private class BlockingLogSaver : LogSaver {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val savedLogs = mutableListOf<ServiceLog>()

    override fun saveAll(logs: List<ServiceLog>) {
      started.countDown()
      release.await(1, TimeUnit.SECONDS)
      savedLogs.addAll(logs)
    }
  }
}
