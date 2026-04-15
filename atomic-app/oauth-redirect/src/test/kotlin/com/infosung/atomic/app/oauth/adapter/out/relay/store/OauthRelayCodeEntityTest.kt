package com.infosung.atomic.app.oauth.adapter.out.relay.store

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OauthRelayCodeEntityTest {
  @Test
  fun `new entity should report new until lifecycle callback marks it persisted`() {
    val entity =
        OauthRelayCodeEntity(
            relayCode = "relay-1",
            payloadJson = "{}",
            expiresAt = LocalDateTime.of(2026, 4, 15, 0, 5),
            createdAt = LocalDateTime.of(2026, 4, 15, 0, 0),
        )

    assertTrue(entity.isNew())

    val markNotNew = OauthRelayCodeEntity::class.java.getDeclaredMethod("markNotNew")
    markNotNew.isAccessible = true
    markNotNew.invoke(entity)

    assertFalse(entity.isNew())
  }
}
