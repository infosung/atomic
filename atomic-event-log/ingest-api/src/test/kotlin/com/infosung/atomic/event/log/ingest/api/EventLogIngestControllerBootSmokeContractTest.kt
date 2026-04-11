package com.infosung.atomic.event.log.ingest.api

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.application.port.out.EventLogStore
import com.infosung.atomic.event.log.application.port.out.EventLogStoreAppendResult
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [EventLogIngestControllerBootSmokeContractTest.TestApplication::class],
    properties =
        [
            "atomic.event.log.ingest.enabled=true",
            "atomic.event.log.ingest.mode=ASYNC",
            "atomic.event.log.ingest.endpoint-path=/test/api/event-logs:batch",
            "atomic.event.log.ingest.collector-id-header-name=X-Collector-Id",
            "atomic.event.log.ingest.async.lane-count=4",
            "atomic.event.log.ingest.async.max-buffered-requests-per-lane=64",
            "atomic.event.log.ingest.async.max-buffered-bytes-per-lane=65536",
        ],
)
@AutoConfigureMockMvc
class EventLogIngestControllerBootSmokeContractTest {
  @jakarta.annotation.Resource private lateinit var mockMvc: MockMvc
  @jakarta.annotation.Resource
  private lateinit var eventLogAsyncIngestQueue: EventLogAsyncIngestQueue
  @jakarta.annotation.Resource private lateinit var recordingStore: RecordingStore

  @Test
  fun `boot mvc should expose documented async ingest endpoint`() {
    mockMvc
        .perform(
            post("/test/api/event-logs:batch")
                .contentType("application/json")
                .content(validApiRequestJson()),
        )
        .andExpect(status().isAccepted)
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data.processingMode").value("ASYNC"))
        .andExpect(jsonPath("$.data.processingStatus").value("ENQUEUED"))
        .andExpect(jsonPath("$.data.queuedEventCount").value(1))
  }

  @Test
  fun `boot mvc should keep malformed json as documented 400 response`() {
    mockMvc
        .perform(
            post("/test/api/event-logs:batch").contentType("application/json").content("{"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON_REQUEST"))
        .andExpect(jsonPath("$.message").value("Malformed JSON request body."))
  }

  @Test
  fun `boot mvc should drain lane-partitioned async requests end to end`() {
    val serviceA = "totp"
    val serviceB =
        generateSequence(1) { previous -> previous + 1 }
            .map { "service-$it" }
            .first { candidate -> laneIdFor(candidate) != laneIdFor(serviceA) }

    mockMvc
        .perform(
            post("/test/api/event-logs:batch")
                .contentType("application/json")
                .content(validApiRequestJson(serviceId = serviceA, eventId = "evt-a")),
        )
        .andExpect(status().isAccepted)

    mockMvc
        .perform(
            post("/test/api/event-logs:batch")
                .contentType("application/json")
                .content(validApiRequestJson(serviceId = serviceB, eventId = "evt-b")),
        )
        .andExpect(status().isAccepted)

    eventually {
      val appendedServiceIds = recordingStore.appendedServiceIds.toSet()
      kotlin.test.assertTrue(appendedServiceIds.contains(serviceA))
      kotlin.test.assertTrue(appendedServiceIds.contains(serviceB))
      kotlin.test.assertTrue(
          eventLogAsyncIngestQueue.checkpoint(laneIdFor(serviceA)).processedSequence > 0,
      )
      kotlin.test.assertTrue(
          eventLogAsyncIngestQueue.checkpoint(laneIdFor(serviceB)).processedSequence > 0,
      )
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ImportAutoConfiguration(
      com.infosung.atomic.event.log.ingest.api.autoconfigure
          .AtomicEventLogIngestApiAutoConfiguration::class,
  )
  class TestApplication {
    @Bean internal fun eventLogStore(): RecordingStore = RecordingStore()
  }

  private fun validApiRequestJson(serviceId: String = "totp", eventId: String = "evt-1"): String =
      """
        {
          "schemaVersion": 1,
          "serviceId": "$serviceId",
          "events": [
            {
              "eventId": "$eventId",
              "eventName": "api.request",
              "eventType": "REQUEST",
              "occurredAt": "2026-04-10T00:00:00Z",
              "platform": "API",
              "platformPayload": {
                "httpMethod": "POST",
                "endpoint": "/api/v1/totp"
              },
              "businessPayload": {
                "success": true
              }
            }
          ]
        }
      """
          .trimIndent()

  private fun laneIdFor(serviceId: String): String {
    val laneIndex = Math.floorMod(serviceId.hashCode(), eventLogAsyncIngestQueue.laneIds().size)
    return "lane-${laneIndex.toString().padStart(3, '0')}"
  }

  private fun eventually(block: () -> Unit) {
    repeat(40) {
      runCatching(block).onSuccess {
        return
      }
      Thread.sleep(25)
    }
    block()
  }

  class RecordingStore : EventLogStore {
    val appendedServiceIds = CopyOnWriteArrayList<String>()

    override fun append(records: List<EventLogRecord>): List<EventLogStoreAppendResult> {
      appendedServiceIds += records.map { it.serviceId }
      return records.map { EventLogStoreAppendResult.ACCEPTED }
    }
  }
}
