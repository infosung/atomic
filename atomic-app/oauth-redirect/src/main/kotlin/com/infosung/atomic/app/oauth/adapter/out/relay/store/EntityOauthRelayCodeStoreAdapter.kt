package com.infosung.atomic.app.oauth.adapter.out.relay.store

import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import java.sql.Timestamp
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/**
 * Adapter-backed RDB table relay store implementation.
 *
 * Expiration is checked on consume (`pop`). Run a periodic cleanup job to remove expired rows that
 * were never consumed.
 */
open class EntityOauthRelayCodeStoreAdapter(
    private val jdbcOperations: JdbcOperations,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
    private val timeProvider: TimeProvider = TimeProvider(),
    tableName: String,
) : OauthRelayCodeStore {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val safeTableName = OauthRelayCodeTableNamePolicy.validateOrThrow(tableName)

  override fun save(
      relayCode: String,
      payload: OauthRelayPayload,
      expiresAt: Instant,
  ) {
    val payloadJson = objectMapper.writeValueAsString(payload)
    val now = timeProvider.nowInstant()

    transactionTemplate.executeWithoutResult {
      jdbcOperations.update("DELETE FROM $safeTableName WHERE relay_code = ?", relayCode)
      jdbcOperations.update(
          """
          INSERT INTO $safeTableName (relay_code, payload_json, expires_at, created_at)
          VALUES (?, ?, ?, ?)
          """
              .trimIndent(),
          relayCode,
          payloadJson,
          Timestamp.from(expiresAt),
          Timestamp.from(now),
      )
    }

    log.trace(
        "Stored oauth relayCode in entity store: tableName={}, relayCodeLength={}",
        safeTableName,
        relayCode.length,
    )
  }

  override fun pop(
      relayCode: String,
      now: Instant,
  ): OauthRelayPayload? {
    return transactionTemplate.execute<OauthRelayPayload?> {
      val selected =
          jdbcOperations.query(
              "SELECT payload_json, expires_at FROM $safeTableName WHERE relay_code = ? FOR UPDATE",
              ResultSetExtractor<SelectedRelay?> { rs ->
                if (!rs.next()) {
                  return@ResultSetExtractor null
                }
                SelectedRelay(
                    payloadJson = rs.getString("payload_json"),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                )
              },
              relayCode,
          ) ?: return@execute null

      jdbcOperations.update("DELETE FROM $safeTableName WHERE relay_code = ?", relayCode)

      if (!selected.expiresAt.isAfter(now)) {
        return@execute null
      }

      objectMapper.readValue(selected.payloadJson, OauthRelayPayload::class.java)
    }
  }

  private data class SelectedRelay(
      val payloadJson: String,
      val expiresAt: Instant,
  )
}
