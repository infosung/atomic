package com.infosung.atomic.app.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OauthRedirectPackageBoundaryContractTest {
  @Test
  fun `oauth redirect root relay stores should delegate into adapter out relay store package`() {
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store",
        InMemoryOauthRelayCodeStore::class.java.superclass.packageName,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store",
        CacheOauthRelayCodeStore::class.java.superclass.packageName,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store",
        EntityOauthRelayCodeStore::class.java.superclass.packageName,
    )
  }

  @Test
  fun `oauth redirect root relay store types should remain exported compatibility seams`() {
    assertEquals(
        "com.infosung.atomic.app.oauth.InMemoryOauthRelayCodeStore",
        InMemoryOauthRelayCodeStore::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.CacheOauthRelayCodeStore",
        CacheOauthRelayCodeStore::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.EntityOauthRelayCodeStore",
        EntityOauthRelayCodeStore::class.java.name,
    )
    assertNotNull(InMemoryOauthRelayCodeStore::class.java.declaredConstructors.firstOrNull())
    assertNotNull(CacheOauthRelayCodeStore::class.java.declaredConstructors.firstOrNull())
    assertNotNull(EntityOauthRelayCodeStore::class.java.declaredConstructors.firstOrNull())
  }
}
