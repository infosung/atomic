package com.infosung.atomic.event.log.ingest.api.autoconfigure

import com.infosung.atomic.event.log.application.port.`in`.IngestEventLogUseCase
import com.infosung.atomic.event.log.application.port.out.EventLogStore
import com.infosung.atomic.event.log.application.service.EventLogIngestionService
import com.infosung.atomic.event.log.ingest.api.adapter.`in`.web.EventLogIngestRequestMapper
import com.infosung.atomic.event.log.ingest.api.adapter.out.authorization.AllowAllEventLogIngestRequestAuthorizer
import com.infosung.atomic.event.log.ingest.api.adapter.out.context.DefaultEventLogIngestContextResolver
import com.infosung.atomic.event.log.ingest.api.adapter.out.queue.memory.HashPartitionedInMemoryEventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.adapter.out.queue.memory.InMemoryEventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.adapter.out.worker.EventLogAsyncIngestWorkerLifecycle
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueuePolicy
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiMode
import com.infosung.atomic.event.log.ingest.api.application.port.`in`.IngestEventLogApiUseCase
import com.infosung.atomic.event.log.ingest.api.application.port.out.AuthorizeEventLogIngestRequestPort
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort
import com.infosung.atomic.event.log.ingest.api.application.port.out.ResolveEventLogIngestContextPort
import com.infosung.atomic.event.log.ingest.api.application.service.IngestEventLogApiService
import com.infosung.atomic.event.log.ingest.api.application.service.ProcessQueuedEventLogBatchesService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnProperty(
    prefix = "atomic.event.log.ingest",
    name = ["enabled"],
    havingValue = "true",
)
class AtomicEventLogIngestApiCoreAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  @ConditionalOnMissingBean
  fun ingestEventLogUseCase(
      eventLogStoreProvider: ObjectProvider<EventLogStore>,
  ): IngestEventLogUseCase {
    val eventLogStore =
        eventLogStoreProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.event.log.ingest.enabled=true requires EventLogStore. Configure a store bean or override IngestEventLogUseCase.",
                )
                .also {
                  log.error(
                      "Event log ingest core auto-configuration fail-fast: EventLogStore is missing while atomic.event.log.ingest.enabled=true.",
                  )
                }
    return EventLogIngestionService(store = eventLogStore)
  }

  @Bean
  @ConditionalOnMissingBean
  fun eventLogIngestRequestMapper(): EventLogIngestRequestMapper = EventLogIngestRequestMapper()

  @Bean
  @ConditionalOnMissingBean
  fun mapEventLogIngestIntakeBatchPort(
      eventLogIngestRequestMapper: EventLogIngestRequestMapper,
  ): MapEventLogIngestIntakeBatchPort = eventLogIngestRequestMapper

  @Bean
  @ConditionalOnMissingBean
  fun authorizeEventLogIngestRequestPort(): AuthorizeEventLogIngestRequestPort {
    return AllowAllEventLogIngestRequestAuthorizer()
  }

  @Bean
  @ConditionalOnMissingBean
  fun resolveEventLogIngestContextPort(
      properties: AtomicEventLogIngestApiProperties,
  ): ResolveEventLogIngestContextPort {
    return DefaultEventLogIngestContextResolver(
        collectorIdHeaderName = properties.collectorIdHeaderName,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.event.log.ingest",
      name = ["mode"],
      havingValue = "ASYNC",
      matchIfMissing = true,
  )
  fun eventLogAsyncIngestQueue(
      properties: AtomicEventLogIngestApiProperties,
  ): EventLogAsyncIngestQueue {
    val queuePolicy =
        EventLogAsyncIngestQueuePolicy(
            maxBufferedRequestsPerLane = properties.async.maxBufferedRequestsPerLane,
            maxBufferedBytesPerLane = properties.async.maxBufferedBytesPerLane,
            enqueueTimeout = properties.async.enqueueTimeout,
        )
    return if (properties.async.laneCount <= 1) {
      InMemoryEventLogAsyncIngestQueue(
          queuePolicy = queuePolicy,
      )
    } else {
      HashPartitionedInMemoryEventLogAsyncIngestQueue(
          laneCount = properties.async.laneCount,
          queuePolicy = queuePolicy,
      )
    }
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.event.log.ingest",
      name = ["mode"],
      havingValue = "ASYNC",
      matchIfMissing = true,
  )
  fun processQueuedEventLogBatchesService(
      eventLogAsyncIngestQueueProvider: ObjectProvider<EventLogAsyncIngestQueue>,
      ingestEventLogUseCase: IngestEventLogUseCase,
      mapEventLogIngestIntakeBatchPort: MapEventLogIngestIntakeBatchPort,
  ): ProcessQueuedEventLogBatchesService {
    val queue =
        eventLogAsyncIngestQueueProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.event.log.ingest.mode=ASYNC requires EventLogAsyncIngestQueue.",
                )
                .also {
                  log.error(
                      "Event log ingest async auto-configuration fail-fast: EventLogAsyncIngestQueue is missing while mode=ASYNC.",
                  )
                }
    return ProcessQueuedEventLogBatchesService(
        queue = queue,
        ingestEventLogUseCase = ingestEventLogUseCase,
        mapEventLogIngestIntakeBatchPort = mapEventLogIngestIntakeBatchPort,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.event.log.ingest",
      name = ["mode"],
      havingValue = "ASYNC",
      matchIfMissing = true,
  )
  fun eventLogAsyncIngestWorkerLifecycle(
      properties: AtomicEventLogIngestApiProperties,
      processQueuedEventLogBatchesServiceProvider:
          ObjectProvider<ProcessQueuedEventLogBatchesService>,
  ): EventLogAsyncIngestWorkerLifecycle {
    val processService =
        processQueuedEventLogBatchesServiceProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.event.log.ingest.mode=ASYNC requires ProcessQueuedEventLogBatchesService.",
                )
                .also {
                  log.error(
                      "Event log ingest async worker auto-configuration fail-fast: ProcessQueuedEventLogBatchesService is missing while mode=ASYNC.",
                  )
                }
    return EventLogAsyncIngestWorkerLifecycle(
        processService = processService,
        pollDelay = properties.async.workerPollDelay,
        pollLimit = properties.async.workerPollLimit,
        shutdownDrainTimeout = properties.async.shutdownDrainTimeout,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun ingestEventLogApiUseCase(
      properties: AtomicEventLogIngestApiProperties,
      ingestEventLogUseCaseProvider: ObjectProvider<IngestEventLogUseCase>,
      authorizeEventLogIngestRequestPort: AuthorizeEventLogIngestRequestPort,
      resolveEventLogIngestContextPort: ResolveEventLogIngestContextPort,
      mapEventLogIngestIntakeBatchPort: MapEventLogIngestIntakeBatchPort,
      eventLogAsyncIngestQueueProvider: ObjectProvider<EventLogAsyncIngestQueue>,
  ): IngestEventLogApiUseCase {
    val ingestEventLogUseCase =
        ingestEventLogUseCaseProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.event.log.ingest.enabled=true requires IngestEventLogUseCase. Configure EventLogStore or provide a custom use-case bean.",
                )
                .also {
                  log.error(
                      "Event log ingest API auto-configuration fail-fast: IngestEventLogUseCase is missing while atomic.event.log.ingest.enabled=true.",
                  )
                }
    return IngestEventLogApiService(
        mode = properties.mode,
        authorizeRequestPort = authorizeEventLogIngestRequestPort,
        resolveContextPort = resolveEventLogIngestContextPort,
        ingestEventLogUseCase = ingestEventLogUseCase,
        mapEventLogIngestIntakeBatchPort = mapEventLogIngestIntakeBatchPort,
        asyncIngestQueue =
            eventLogAsyncIngestQueueProvider.getIfAvailable().also {
              if (properties.mode == EventLogIngestApiMode.ASYNC && it == null) {
                throw IllegalStateException(
                    "atomic.event.log.ingest.mode=ASYNC requires EventLogAsyncIngestQueue.",
                )
              }
            },
    )
  }
}
