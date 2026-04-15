package com.infosung.atomic.app.oauth.adapter.out.relay.store

import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper

class JpaOauthRelayCodeStoreTest {
  @Test
  fun `save should persist without immediate flush`() {
    val repository = mock(OauthRelayCodeRepository::class.java)
    val store = newStore(repository)

    store.save(
        relayCode = "relay-1",
        payload = OauthRelayPayload(provider = OauthProviderName.GOOGLE, accessToken = "access"),
        expiresAt = Instant.parse("2026-04-15T00:05:00Z"),
    )

    verify(repository).save(any(OauthRelayCodeEntity::class.java))
    verify(repository, never()).saveAndFlush(any(OauthRelayCodeEntity::class.java))
  }

  @Test
  fun `pop should delete without explicit flush`() {
    val repository = mock(OauthRelayCodeRepository::class.java)
    val store = newStore(repository)
    val entity =
        OauthRelayCodeEntity(
            relayCode = "relay-1",
            payloadJson =
                jacksonObjectMapper()
                    .writeValueAsString(
                        OauthRelayPayload(
                            provider = OauthProviderName.KAKAO,
                            accessToken = "access-token",
                        ),
                    ),
            expiresAt = LocalDateTime.of(2026, 4, 15, 0, 5),
            createdAt = LocalDateTime.of(2026, 4, 15, 0, 0),
        )
    `when`(repository.findLockedByRelayCode("relay-1")).thenReturn(entity)

    val popped = store.pop("relay-1", Instant.parse("2026-04-15T00:01:00Z"))

    assertNotNull(popped)
    assertEquals(OauthProviderName.KAKAO, popped.provider)
    assertEquals("access-token", popped.accessToken)
    verify(repository).delete(entity)
    verify(repository, never()).flush()
  }

  private fun newStore(repository: OauthRelayCodeRepository): JpaOauthRelayCodeStore {
    return JpaOauthRelayCodeStore(
        oauthRelayCodeRepository = repository,
        transactionTemplate = TransactionTemplate(NoOpPlatformTransactionManager()),
        objectMapper = jacksonObjectMapper(),
        timeProvider =
            TimeProvider(Clock.fixed(Instant.parse("2026-04-15T00:00:00Z"), ZoneOffset.UTC)),
    )
  }

  private class NoOpPlatformTransactionManager : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
        SimpleTransactionStatus()

    override fun commit(status: TransactionStatus) = Unit

    override fun rollback(status: TransactionStatus) = Unit
  }
}
