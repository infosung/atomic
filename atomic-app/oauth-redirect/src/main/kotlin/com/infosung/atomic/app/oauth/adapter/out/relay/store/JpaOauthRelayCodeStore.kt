package com.infosung.atomic.app.oauth.adapter.out.relay.store

import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

internal class JpaOauthRelayCodeStore(
    private val oauthRelayCodeRepository: OauthRelayCodeRepository,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
    private val timeProvider: TimeProvider = TimeProvider(),
) : OauthRelayCodeStore {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun save(
      relayCode: String,
      payload: OauthRelayPayload,
      expiresAt: Instant,
  ) {
    val payloadJson = objectMapper.writeValueAsString(payload)
    val now = timeProvider.nowInstant()

    transactionTemplate.executeWithoutResult {
      oauthRelayCodeRepository.save(
          OauthRelayCodeEntity(
              relayCode = relayCode,
              payloadJson = payloadJson,
              expiresAt = expiresAt.toUtcLocalDateTime(),
              createdAt = now.toUtcLocalDateTime(),
          ),
      )
    }

    log.trace("Stored oauth relayCode in JPA store: relayCodeLength={}", relayCode.length)
  }

  override fun pop(
      relayCode: String,
      now: Instant,
  ): OauthRelayPayload? {
    return transactionTemplate.execute<OauthRelayPayload?> {
      val selected =
          oauthRelayCodeRepository.findLockedByRelayCode(relayCode) ?: return@execute null
      oauthRelayCodeRepository.delete(selected)

      if (!selected.expiresAt.isAfter(now.toUtcLocalDateTime())) {
        log.debug(
            "Discarded expired oauth relayCode in JPA store: relayCodeLength={}", relayCode.length)
        return@execute null
      }

      objectMapper.readValue(selected.payloadJson, OauthRelayPayload::class.java)
    }
  }

  private fun Instant.toUtcLocalDateTime(): LocalDateTime =
      LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
