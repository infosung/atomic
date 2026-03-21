package com.infosung.atomic.app.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OauthRedirectPackageBoundaryContractTest {
  @Test
  fun `oauth redirect legal topology should export web entry types from adapter in web`() {
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.in.web.AppOauthRedirectController",
        requiredClass("com.infosung.atomic.app.oauth.adapter.in.web.AppOauthRedirectController")
            .name,
    )
  }

  @Test
  fun `oauth redirect legal topology should export relay store seam from adapter out relay store`() {
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore",
        requiredClass("com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStore",
        requiredClass(
                "com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStore")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store.CacheOauthRelayCodeStore",
        requiredClass(
                "com.infosung.atomic.app.oauth.adapter.out.relay.store.CacheOauthRelayCodeStore")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store.EntityOauthRelayCodeStore",
        requiredClass(
                "com.infosung.atomic.app.oauth.adapter.out.relay.store.EntityOauthRelayCodeStore")
            .name,
    )
  }

  @Test
  fun `oauth redirect legal topology should export relay payload from domain`() {
    assertEquals(
        "com.infosung.atomic.app.oauth.domain.OauthRelayPayload",
        requiredClass("com.infosung.atomic.app.oauth.domain.OauthRelayPayload").name,
    )
  }

  @Test
  fun `oauth redirect legal topology should not keep legacy root wrapper classes`() {
    listOf(
            "com.infosung.atomic.app.oauth.AppOauthRedirectController",
            "com.infosung.atomic.app.oauth.AppOauthRedirectHttpExceptionHandler",
            "com.infosung.atomic.app.oauth.AppOauthRedirectService",
            "com.infosung.atomic.app.oauth.AppOauthRelayCodeService",
            "com.infosung.atomic.app.oauth.OauthRelayCodeStore",
            "com.infosung.atomic.app.oauth.InMemoryOauthRelayCodeStore",
            "com.infosung.atomic.app.oauth.CacheOauthRelayCodeStore",
            "com.infosung.atomic.app.oauth.EntityOauthRelayCodeStore",
            "com.infosung.atomic.app.oauth.OauthRelayPayload",
        )
        .forEach { legacyClassName ->
          val exception = assertFailsWith<ClassNotFoundException> { Class.forName(legacyClassName) }
          assertTrue(exception.message?.contains(legacyClassName) != false)
        }

    assertFailsWith<ClassNotFoundException> {
      Class.forName(
          "com.infosung.atomic.app.oauth.adapter.in.web.AppOauthRedirectHttpExceptionHandler",
      )
    }
  }

  private fun requiredClass(name: String): Class<*> {
    return Class.forName(name)
  }
}
