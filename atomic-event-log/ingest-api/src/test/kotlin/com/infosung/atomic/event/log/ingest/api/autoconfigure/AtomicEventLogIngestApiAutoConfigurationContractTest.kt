package com.infosung.atomic.event.log.ingest.api.autoconfigure

import com.infosung.atomic.event.log.application.port.`in`.IngestEventLogUseCase
import com.infosung.atomic.event.log.application.port.out.EventLogStore
import com.infosung.atomic.event.log.application.port.out.EventLogStoreAppendResult
import com.infosung.atomic.event.log.application.service.EventLogIngestionService
import com.infosung.atomic.event.log.ingest.api.adapter.`in`.web.EventLogIngestController
import com.infosung.atomic.event.log.ingest.api.adapter.`in`.web.EventLogIngestHttpExceptionHandler
import com.infosung.atomic.event.log.ingest.api.adapter.`in`.web.EventLogIngestRequestMapper
import com.infosung.atomic.event.log.ingest.api.adapter.out.authorization.AllowAllEventLogIngestRequestAuthorizer
import com.infosung.atomic.event.log.ingest.api.adapter.out.context.DefaultEventLogIngestContextResolver
import com.infosung.atomic.event.log.ingest.api.adapter.out.queue.memory.HashPartitionedInMemoryEventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.adapter.out.queue.memory.InMemoryEventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.adapter.out.worker.EventLogAsyncIngestWorkerLifecycle
import com.infosung.atomic.event.log.ingest.api.application.port.`in`.IngestEventLogApiUseCase
import com.infosung.atomic.event.log.ingest.api.application.port.out.AuthorizeEventLogIngestRequestPort
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort
import com.infosung.atomic.event.log.ingest.api.application.port.out.ResolveEventLogIngestContextPort
import com.infosung.atomic.event.log.ingest.api.application.service.IngestEventLogApiService
import com.infosung.atomic.event.log.ingest.api.application.service.ProcessQueuedEventLogBatchesService
import java.lang.reflect.Method
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import

class AtomicEventLogIngestApiAutoConfigurationContractTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AtomicEventLogIngestApiAutoConfiguration::class.java),
          )

  @Test
  fun `disabled ingest api should not register controller or use case beans`() {
    contextRunner.run { context ->
      assertTrue(context.getBeansOfType(IngestEventLogUseCase::class.java).isEmpty())
      assertTrue(context.getBeansOfType(IngestEventLogApiUseCase::class.java).isEmpty())
      assertTrue(context.getBeansOfType(EventLogIngestController::class.java).isEmpty())
    }
  }

  @Test
  fun `umbrella auto configuration should import core and web configs`() {
    val imported =
        AtomicEventLogIngestApiAutoConfiguration::class
            .java
            .getAnnotation(Import::class.java)
            .value
            .toSet()

    assertEquals(
        setOf(
            AtomicEventLogIngestApiCoreAutoConfiguration::class,
            AtomicEventLogIngestApiWebAutoConfiguration::class,
        ),
        imported,
    )
  }

  @Test
  fun `enabled ingest api should fail fast when no store or use case override exists`() {
    contextRunner.withPropertyValues("atomic.event.log.ingest.enabled=true").run { context ->
      assertNotNull(context.startupFailure)
      assertTrue(context.startupFailure!!.message!!.contains("requires EventLogStore"))
    }
  }

  @Test
  fun `async mode should create default queue worker and controller when store exists`() {
    contextRunner
        .withPropertyValues(
            "atomic.event.log.ingest.enabled=true",
            "atomic.event.log.ingest.mode=ASYNC",
            "atomic.event.log.ingest.endpoint-path=/internal/event-logs:batch",
            "atomic.event.log.ingest.collector-id-header-name=X-Collector-Id",
            "atomic.event.log.ingest.async.lane-count=4",
            "atomic.event.log.ingest.async.max-buffered-requests-per-lane=64",
            "atomic.event.log.ingest.async.max-buffered-bytes-per-lane=65536",
        )
        .withBean(EventLogStore::class.java, Supplier { acceptingStore() })
        .run { context ->
          assertIs<EventLogIngestionService>(context.getBean(IngestEventLogUseCase::class.java))
          assertIs<EventLogIngestRequestMapper>(
              context.getBean(EventLogIngestRequestMapper::class.java))
          assertIs<MapEventLogIngestIntakeBatchPort>(
              context.getBean(MapEventLogIngestIntakeBatchPort::class.java),
          )
          assertIs<IngestEventLogApiService>(context.getBean(IngestEventLogApiUseCase::class.java))
          assertIs<AllowAllEventLogIngestRequestAuthorizer>(
              context.getBean(AuthorizeEventLogIngestRequestPort::class.java),
          )
          assertIs<DefaultEventLogIngestContextResolver>(
              context.getBean(ResolveEventLogIngestContextPort::class.java),
          )
          assertIs<HashPartitionedInMemoryEventLogAsyncIngestQueue>(
              context.getBean(EventLogAsyncIngestQueue::class.java))
          assertIs<ProcessQueuedEventLogBatchesService>(
              context.getBean(ProcessQueuedEventLogBatchesService::class.java),
          )
          assertIs<EventLogAsyncIngestWorkerLifecycle>(
              context.getBean(EventLogAsyncIngestWorkerLifecycle::class.java),
          )
          assertIs<EventLogIngestController>(context.getBean(EventLogIngestController::class.java))
          assertIs<EventLogIngestHttpExceptionHandler>(
              context.getBean(EventLogIngestHttpExceptionHandler::class.java),
          )
        }
  }

  @Test
  fun `sync mode should not create async queue or worker`() {
    contextRunner
        .withPropertyValues(
            "atomic.event.log.ingest.enabled=true",
            "atomic.event.log.ingest.mode=SYNC",
        )
        .withBean(EventLogStore::class.java, Supplier { acceptingStore() })
        .run { context ->
          assertIs<IngestEventLogApiService>(context.getBean(IngestEventLogApiUseCase::class.java))
          assertTrue(context.getBeansOfType(EventLogAsyncIngestQueue::class.java).isEmpty())
          assertTrue(
              context.getBeansOfType(ProcessQueuedEventLogBatchesService::class.java).isEmpty())
          assertTrue(
              context.getBeansOfType(EventLogAsyncIngestWorkerLifecycle::class.java).isEmpty())
        }
  }

  @Test
  fun `single lane async mode should create single in-memory queue`() {
    contextRunner
        .withPropertyValues(
            "atomic.event.log.ingest.enabled=true",
            "atomic.event.log.ingest.mode=ASYNC",
            "atomic.event.log.ingest.async.lane-count=1",
        )
        .withBean(EventLogStore::class.java, Supplier { acceptingStore() })
        .run { context ->
          assertIs<InMemoryEventLogAsyncIngestQueue>(
              context.getBean(EventLogAsyncIngestQueue::class.java))
        }
  }

  @Test
  fun `custom authorizer resolver and ingest use case should suppress defaults`() {
    val customUseCase = NoopIngestEventLogUseCase()
    val customAuthorizer = NoopAuthorizeEventLogIngestRequestPort()
    val customResolver = NoopResolveEventLogIngestContextPort()

    contextRunner
        .withPropertyValues(
            "atomic.event.log.ingest.enabled=true", "atomic.event.log.ingest.mode=SYNC")
        .withBean(IngestEventLogUseCase::class.java, Supplier { customUseCase })
        .withBean(AuthorizeEventLogIngestRequestPort::class.java, Supplier { customAuthorizer })
        .withBean(ResolveEventLogIngestContextPort::class.java, Supplier { customResolver })
        .run { context ->
          assertSame(customUseCase, context.getBean(IngestEventLogUseCase::class.java))
          assertSame(
              customAuthorizer,
              context.getBean(AuthorizeEventLogIngestRequestPort::class.java),
          )
          assertSame(
              customResolver,
              context.getBean(ResolveEventLogIngestContextPort::class.java),
          )
          assertEquals(0, context.getBeanNamesForType(EventLogStore::class.java).size)
          assertNotNull(context.getBean(EventLogIngestController::class.java))
        }
  }

  @Test
  fun `auto configuration should keep missing bean guards on exported seams`() {
    val coreBeanMethods = AtomicEventLogIngestApiCoreAutoConfiguration::class.java.declaredMethods
    val webBeanMethods = AtomicEventLogIngestApiWebAutoConfiguration::class.java.declaredMethods

    assertTrue(
        coreBeanMethods
            .single(::isIngestEventLogUseCaseBeanMethod)
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
    assertTrue(
        coreBeanMethods
            .single(::isAuthorizeRequestPortBeanMethod)
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
    assertTrue(
        coreBeanMethods
            .single(::isResolveContextPortBeanMethod)
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
    assertTrue(
        coreBeanMethods
            .single(::isIngestEventLogApiUseCaseBeanMethod)
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
    assertTrue(
        webBeanMethods
            .single(::isEventLogIngestControllerBeanMethod)
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
  }

  private fun isIngestEventLogUseCaseBeanMethod(method: Method): Boolean {
    return method.name.substringBefore('$') == "ingestEventLogUseCase" &&
        method.returnType == IngestEventLogUseCase::class.java
  }

  private fun isAuthorizeRequestPortBeanMethod(method: Method): Boolean {
    return method.name.substringBefore('$') == "authorizeEventLogIngestRequestPort" &&
        method.returnType == AuthorizeEventLogIngestRequestPort::class.java
  }

  private fun isResolveContextPortBeanMethod(method: Method): Boolean {
    return method.name.substringBefore('$') == "resolveEventLogIngestContextPort" &&
        method.returnType == ResolveEventLogIngestContextPort::class.java
  }

  private fun isIngestEventLogApiUseCaseBeanMethod(method: Method): Boolean {
    return method.name.substringBefore('$') == "ingestEventLogApiUseCase" &&
        method.returnType == IngestEventLogApiUseCase::class.java
  }

  private fun isEventLogIngestControllerBeanMethod(method: Method): Boolean {
    return method.name.substringBefore('$') == "eventLogIngestController" &&
        method.returnType == EventLogIngestController::class.java
  }

  private fun acceptingStore(): EventLogStore = EventLogStore { records ->
    records.map { EventLogStoreAppendResult.ACCEPTED }
  }

  private class NoopIngestEventLogUseCase : IngestEventLogUseCase {
    override fun ingest(
        batch: com.infosung.atomic.event.log.application.model.EventLogBatch,
        context: com.infosung.atomic.event.log.application.model.EventLogIngestContext,
    ) = throw UnsupportedOperationException("test should not invoke ingest")
  }

  private class NoopAuthorizeEventLogIngestRequestPort : AuthorizeEventLogIngestRequestPort {
    override fun authorize(
        batch: com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch,
        requestMetadata:
            com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata,
    ) = Unit
  }

  private class NoopResolveEventLogIngestContextPort : ResolveEventLogIngestContextPort {
    override fun resolve(
        batch: com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch,
        requestMetadata:
            com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata,
    ) = com.infosung.atomic.event.log.application.model.EventLogIngestContext()
  }
}
