package com.infosung.atomic.app.oauth

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.module.kotlin.jacksonObjectMapper

@Testcontainers(disabledWithoutDocker = true)
class EntityOauthRelayCodeStoreMigrationAssetContractTest {
  @Test
  fun `official oauth relay sql asset should support entity relay code store`() {
    val dataSource = newDataSource()
    ResourceDatabasePopulator(
            false,
            false,
            "UTF-8",
            ClassPathResource("META-INF/atomic/sql/postgresql/atomic_oauth_relay_code.sql"),
        )
        .execute(dataSource)

    val jdbcTemplate = JdbcTemplate(dataSource)
    val store =
        EntityOauthRelayCodeStore(
            jdbcOperations = jdbcTemplate,
            transactionTemplate = TransactionTemplate(DataSourceTransactionManager(dataSource)),
            objectMapper = jacksonObjectMapper(),
            timeProvider =
                TimeProvider(Clock.fixed(Instant.parse("2026-03-14T00:00:00Z"), ZoneOffset.UTC)),
            tableName = "atomic_oauth_relay_code",
        )

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

    val indexes =
        jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename = 'atomic_oauth_relay_code'
            """
                .trimIndent(),
            String::class.java,
        )

    assertTrue(indexes.contains("idx_atomic_oauth_relay_code_expires_at"))
  }

  private fun newDataSource(): DriverManagerDataSource {
    return DriverManagerDataSource().apply {
      setDriverClassName(postgres.driverClassName)
      url = postgres.jdbcUrl
      username = postgres.username
      password = postgres.password
    }
  }

  companion object {
    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
  }
}
