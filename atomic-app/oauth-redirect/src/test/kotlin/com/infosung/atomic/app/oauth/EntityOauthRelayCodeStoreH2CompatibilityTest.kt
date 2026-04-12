package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.relay.store.EntityOauthRelayCodeStore
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.database.JdbcTableIndexMetadataLoader
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
import tools.jackson.module.kotlin.jacksonObjectMapper

class EntityOauthRelayCodeStoreH2CompatibilityTest {
  @Test
  fun `h2 asset should support entity relay code store`() {
    val dataSource =
        DriverManagerDataSource().apply {
          setDriverClassName("org.h2.Driver")
          url = "jdbc:h2:mem:oauth_relay_h2;DB_CLOSE_DELAY=-1"
          username = "sa"
          password = ""
        }
    ResourceDatabasePopulator(
            false,
            false,
            "UTF-8",
            ClassPathResource("META-INF/atomic/sql/h2/atomic_oauth_relay_code.sql"),
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

    val indexes = JdbcTableIndexMetadataLoader(dataSource).loadIndexes("atomic_oauth_relay_code")
    assertTrue(indexes.containsKey("idx_atomic_oauth_relay_code_expires_at"))
  }
}
