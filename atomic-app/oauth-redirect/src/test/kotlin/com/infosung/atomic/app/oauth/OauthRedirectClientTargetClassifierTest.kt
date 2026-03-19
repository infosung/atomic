package com.infosung.atomic.app.oauth

import kotlin.test.Test
import kotlin.test.assertEquals

class OauthRedirectClientTargetClassifierTest {
  @Test
  fun `classify should treat https frontend uri as web target`() {
    assertEquals(
        OauthRedirectClientTarget.WEB,
        OauthRedirectClientTargetClassifier.classify(
            redirectUri = "https://frontend.example.com/oauth/callback",
        ),
    )
  }

  @Test
  fun `classify should keep https app link uri in web target bucket`() {
    assertEquals(
        OauthRedirectClientTarget.WEB,
        OauthRedirectClientTargetClassifier.classify(
            redirectUri = "https://app.example.com/mobile/oauth/callback",
        ),
    )
  }

  @Test
  fun `classify should treat custom scheme uri as app link target`() {
    assertEquals(
        OauthRedirectClientTarget.APP_LINK,
        OauthRedirectClientTargetClassifier.classify(
            redirectUri = "myapp://oauth/callback",
        ),
    )
  }

  @Test
  fun `classify should treat loopback uri as loopback target`() {
    assertEquals(
        OauthRedirectClientTarget.LOOPBACK,
        OauthRedirectClientTargetClassifier.classify(
            redirectUri = "http://127.0.0.1:49152/oauth/callback",
        ),
    )
    assertEquals(
        OauthRedirectClientTarget.LOOPBACK,
        OauthRedirectClientTargetClassifier.classify(
            redirectUri = "http://localhost:49152/oauth/callback",
        ),
    )
  }
}
