package com.infosung.atomic.event.log.ingest.api.autoconfigure

import com.infosung.atomic.event.log.ingest.api.adapter.`in`.web.EventLogIngestController
import com.infosung.atomic.event.log.ingest.api.adapter.`in`.web.EventLogIngestHttpExceptionHandler
import com.infosung.atomic.event.log.ingest.api.adapter.`in`.web.EventLogIngestRequestMapper
import com.infosung.atomic.event.log.ingest.api.application.port.`in`.IngestEventLogApiUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@AutoConfiguration(after = [AtomicEventLogIngestApiCoreAutoConfiguration::class])
@ConditionalOnClass(name = ["org.springframework.web.bind.annotation.RestController"])
@ConditionalOnProperty(
    prefix = "atomic.event.log.ingest",
    name = ["enabled"],
    havingValue = "true",
)
class AtomicEventLogIngestApiWebAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  @ConditionalOnMissingBean
  fun eventLogIngestController(
      ingestEventLogApiUseCaseProvider: ObjectProvider<IngestEventLogApiUseCase>,
      eventLogIngestRequestMapper: EventLogIngestRequestMapper,
  ): EventLogIngestController {
    val ingestEventLogApiUseCase =
        ingestEventLogApiUseCaseProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.event.log.ingest.enabled=true requires IngestEventLogApiUseCase. Ensure core ingest prerequisites are configured.",
                )
                .also {
                  log.error(
                      "Event log ingest web auto-configuration fail-fast: IngestEventLogApiUseCase is missing while atomic.event.log.ingest.enabled=true.",
                  )
                }
    return EventLogIngestController(
        useCase = ingestEventLogApiUseCase,
        requestMapper = eventLogIngestRequestMapper,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun eventLogIngestHttpExceptionHandler(
      environment: Environment,
  ): EventLogIngestHttpExceptionHandler {
    return EventLogIngestHttpExceptionHandler(environment = environment)
  }
}
