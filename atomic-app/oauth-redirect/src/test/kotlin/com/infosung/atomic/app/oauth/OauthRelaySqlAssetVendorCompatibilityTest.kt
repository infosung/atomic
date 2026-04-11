package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.relay.store.EntityOauthRelayCodeStore
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
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
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.module.kotlin.jacksonObjectMapper

@Testcontainers(disabledWithoutDocker = true)
class OauthRelaySqlAssetVendorCompatibilityTest {
  @Test
  fun `mysql asset should support entity relay code store`() {
    verifyOauthRelayAsset(
        vendor = "mysql",
        dataSource =
            DriverManagerDataSource().apply {
              setDriverClassName(mysql.driverClassName)
              url = mysql.jdbcUrl
              username = mysql.username
              password = mysql.password
            },
    )
  }

  @Test
  fun `mariadb asset should support entity relay code store`() {
    verifyOauthRelayAsset(
        vendor = "mariadb",
        dataSource =
            DriverManagerDataSource().apply {
              setDriverClassName(mariadb.driverClassName)
              url = mariadb.jdbcUrl
              username = mariadb.username
              password = mariadb.password
            },
    )
  }

  private fun verifyOauthRelayAsset(
      vendor: String,
      dataSource: DriverManagerDataSource,
  ) {
    val jdbcTemplate = JdbcTemplate(dataSource)
    jdbcTemplate.execute("DROP TABLE IF EXISTS atomic_oauth_relay_code")
    ResourceDatabasePopulator(
            false,
            false,
            "UTF-8",
            ClassPathResource("META-INF/atomic/sql/$vendor/atomic_oauth_relay_code.sql"),
        )
        .execute(dataSource)

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
            SELECT index_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'atomic_oauth_relay_code'
            """
                .trimIndent(),
            String::class.java,
        )
    assertTrue(indexes.contains("idx_atomic_oauth_relay_code_expires_at"))
  }

  companion object {
    @Container @JvmStatic private val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.4")

    @Container
    @JvmStatic
    private val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:11.4")
  }
}
