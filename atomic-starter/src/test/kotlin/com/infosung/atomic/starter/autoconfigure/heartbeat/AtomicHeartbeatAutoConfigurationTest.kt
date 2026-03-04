package com.infosung.atomic.starter.autoconfigure.heartbeat

import com.infosung.atomic.heartbeat.HeartbeatEvent
import com.infosung.atomic.heartbeat.HeartbeatOrchestrator
import com.infosung.atomic.heartbeat.HeartbeatProvider
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class AtomicHeartbeatAutoConfigurationTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AtomicHeartbeatAutoConfiguration::class.java))

  @Test
  fun `enabled heartbeat should register orchestrator`() {
    contextRunner
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.healthchecks.base-url=https://hc-ping.com/test",
            "atomic.heartbeat.ping.interval=10s",
        )
        .run { context ->
          assertNotNull(context.getBean(HeartbeatOrchestrator::class.java))
          assertNotNull(context.getBean("heartbeatPropertiesValidation"))
        }
  }

  @Test
  fun `db check with missing datasource should be skipped when policy is warn`() {
    contextRunner
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.healthchecks.base-url=https://hc-ping.com/test",
            "atomic.heartbeat.checks.db.enabled=true",
            "atomic.heartbeat.checks.missing-bean-policy=warn",
        )
        .run { context ->
          assertNotNull(context.getBean(HeartbeatOrchestrator::class.java))
          assertTrue(context.startupFailure == null)
        }
  }

  @Test
  fun `db check with missing datasource should fail when policy is fail`() {
    contextRunner
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.healthchecks.base-url=https://hc-ping.com/test",
            "atomic.heartbeat.checks.db.enabled=true",
            "atomic.heartbeat.checks.missing-bean-policy=fail",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("requires DataSource") == true)
        }
  }

  @Test
  fun `redis check with missing bean should be skipped when policy is warn`() {
    contextRunner
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.healthchecks.base-url=https://hc-ping.com/test",
            "atomic.heartbeat.checks.redis.enabled=true",
            "atomic.heartbeat.checks.missing-bean-policy=warn",
        )
        .run { context ->
          assertNotNull(context.getBean(HeartbeatOrchestrator::class.java))
          assertTrue(context.startupFailure == null)
        }
  }

  @Test
  fun `redis check with missing bean should fail when policy is fail`() {
    contextRunner
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.healthchecks.base-url=https://hc-ping.com/test",
            "atomic.heartbeat.checks.redis.enabled=true",
            "atomic.heartbeat.checks.missing-bean-policy=fail",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("requires StringRedisTemplate or RedisConnectionFactory") == true)
        }
  }

  @Test
  fun `leader redis mode should fail when redis dependency is missing`() {
    contextRunner
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.healthchecks.base-url=https://hc-ping.com/test",
            "atomic.heartbeat.dedup.mode=leader",
            "atomic.heartbeat.dedup.leader.backend=redis",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("requires StringRedisTemplate or RedisConnectionFactory") == true)
        }
  }

  @Test
  fun `leader renew interval must be smaller than lease duration`() {
    contextRunner
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.healthchecks.base-url=https://hc-ping.com/test",
            "atomic.heartbeat.dedup.mode=leader",
            "atomic.heartbeat.dedup.leader.renew-interval=60s",
            "atomic.heartbeat.dedup.leader.lease-duration=30s",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("renew-interval") == true)
        }
  }

  @Test
  fun `custom provider type should allow startup without healthchecks url when custom bean exists`() {
    contextRunner
        .withUserConfiguration(CustomHeartbeatProviderConfiguration::class.java)
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.type=custom",
        )
        .run { context ->
          assertNotNull(context.getBean(HeartbeatOrchestrator::class.java))
          assertTrue(context.startupFailure == null)
        }
  }

  @Test
  fun `custom provider type should not require healthchecks timeout values`() {
    contextRunner
        .withUserConfiguration(CustomHeartbeatProviderConfiguration::class.java)
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.type=custom",
            "atomic.heartbeat.provider.timeout=0s",
            "atomic.heartbeat.provider.connect-timeout=0s",
        )
        .run { context ->
          assertNotNull(context.getBean(HeartbeatOrchestrator::class.java))
          assertTrue(context.startupFailure == null)
        }
  }

  @Test
  fun `custom provider type without bean should fail startup`() {
    contextRunner
        .withPropertyValues(
            "atomic.heartbeat.enabled=true",
            "atomic.heartbeat.provider.type=custom",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("custom HeartbeatProvider") == true)
        }
  }

  @Configuration
  class CustomHeartbeatProviderConfiguration {
    @Bean
    fun heartbeatProvider(): HeartbeatProvider = HeartbeatProvider { _: HeartbeatEvent -> }
  }
}
