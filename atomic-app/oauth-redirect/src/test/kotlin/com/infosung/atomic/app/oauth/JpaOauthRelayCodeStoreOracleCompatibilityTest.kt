package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.relay.store.JpaOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeEntity
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeRepository
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.oracle.OracleContainer
import tools.jackson.module.kotlin.jacksonObjectMapper

@EnabledIfEnvironmentVariable(named = "ATOMIC_RUN_ORACLE_COMPATIBILITY", matches = "true")
@DataJpaTest(
    properties =
        [
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:META-INF/atomic/sql/oracle/atomic_oauth_relay_code.sql",
        ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaOauthRelayCodeStoreOracleCompatibilityTest.TestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class JpaOauthRelayCodeStoreOracleCompatibilityTest {
  @Autowired private lateinit var store: OauthRelayCodeStore

  @Test
  fun `jpa relay code store should save and pop on oracle`() {
    store.save(
        relayCode = "relay-1",
        payload =
            OauthRelayPayload(
                provider = OauthProviderName.GOOGLE,
                accessToken = "access-token",
                nonce = "nonce-1",
            ),
        expiresAt = Instant.parse("2026-03-14T00:05:00Z"),
    )

    val popped = store.pop("relay-1", Instant.parse("2026-03-14T00:01:00Z"))

    assertNotNull(popped)
    assertEquals(OauthProviderName.GOOGLE, popped.provider)
    assertEquals("access-token", popped.accessToken)
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = [OauthRelayCodeEntity::class])
  @EnableJpaRepositories(basePackageClasses = [OauthRelayCodeRepository::class])
  class TestConfiguration {
    @Bean
    fun jpaOauthRelayCodeStore(
        oauthRelayCodeRepository: OauthRelayCodeRepository,
        transactionManager: PlatformTransactionManager,
    ): OauthRelayCodeStore {
      return JpaOauthRelayCodeStore(
          oauthRelayCodeRepository = oauthRelayCodeRepository,
          transactionTemplate = TransactionTemplate(transactionManager),
          objectMapper = jacksonObjectMapper(),
          timeProvider =
              TimeProvider(Clock.fixed(Instant.parse("2026-03-14T00:00:00Z"), ZoneOffset.UTC)),
      )
    }
  }

  companion object {
    @Container
    @JvmStatic
    private val oracle: OracleContainer = OracleContainer("gvenzl/oracle-free:23-slim-faststart")

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", oracle::getJdbcUrl)
      registry.add("spring.datasource.username", oracle::getUsername)
      registry.add("spring.datasource.password", oracle::getPassword)
    }
  }
}
