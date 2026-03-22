package com.infosung.atomic.starter.autoconfigure.idempotency

import com.infosung.atomic.spring.idempotency.IdempotencyFilter
import com.infosung.atomic.spring.idempotency.IdempotencyStore
import com.infosung.atomic.spring.idempotency.InMemoryIdempotencyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

class AtomicIdempotencyAutoConfigurationTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AtomicIdempotencyAutoConfiguration::class.java))

  @Test
  fun `enabled idempotency should register default store and filter`() {
    contextRunner.withPropertyValues("atomic.idempotency.enabled=true").run { context ->
      assertIs<InMemoryIdempotencyStore>(context.getBean(IdempotencyStore::class.java))
      assertIs<IdempotencyFilter>(context.getBean(IdempotencyFilter::class.java))
    }
  }

  @Test
  fun `invalid ttl should fail context startup`() {
    contextRunner
        .withPropertyValues(
            "atomic.idempotency.enabled=true",
            "atomic.idempotency.ttl-seconds=0",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("atomic.idempotency.ttl-seconds") == true)
        }
  }

  @Test
  fun `blank replay body omitted header should fail context startup`() {
    contextRunner
        .withPropertyValues(
            "atomic.idempotency.enabled=true",
            "atomic.idempotency.replay-body-omitted-header-name= ",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains("atomic.idempotency.replay-body-omitted-header-name") ==
                  true)
        }
  }

  @Test
  fun `invalid processing ttl should fail context startup`() {
    contextRunner
        .withPropertyValues(
            "atomic.idempotency.enabled=true",
            "atomic.idempotency.processing-ttl-seconds=0",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("atomic.idempotency.processing-ttl-seconds") == true)
        }
  }

  @Test
  fun `blank filter patterns should be ignored when filter is disabled`() {
    contextRunner
        .withPropertyValues(
            "atomic.idempotency.enabled=true",
            "atomic.idempotency.filter.enabled=false",
            "atomic.idempotency.filter.url-patterns[0]= ",
        )
        .run { context ->
          val failure = context.startupFailure
          assertEquals(null, failure)
          assertIs<IdempotencyFilter>(context.getBean(IdempotencyFilter::class.java))
        }
  }

  @Test
  fun `mixed blank filter patterns should fail when filter is enabled`() {
    contextRunner
        .withPropertyValues(
            "atomic.idempotency.enabled=true",
            "atomic.idempotency.filter.enabled=true",
            "atomic.idempotency.filter.url-patterns[0]=/*",
            "atomic.idempotency.filter.url-patterns[1]= ",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("filter.url-patterns") == true)
        }
  }

  @Test
  fun `idempotency filter should use host object mapper for rejection body serialization`() {
    contextRunner
        .withUserConfiguration(IndentedObjectMapperConfiguration::class.java)
        .withPropertyValues(
            "atomic.idempotency.enabled=true",
        )
        .run { context ->
          val filter = context.getBean(IdempotencyFilter::class.java)
          val objectMapperField = IdempotencyFilter::class.java.getDeclaredField("objectMapper")
          objectMapperField.isAccessible = true

          assertSame(hostObjectMapper, objectMapperField.get(filter))
        }
  }

  @Configuration
  class IndentedObjectMapperConfiguration {
    @Bean fun objectMapper(): ObjectMapper = hostObjectMapper
  }

  companion object {
    private val hostObjectMapper = ObjectMapper()
  }
}
