package com.infosung.atomic.app.oauth

import java.net.URI
import java.util.Locale

internal enum class OauthRedirectClientTarget {
  WEB,
  APP_LINK,
  LOOPBACK,
}

internal object OauthRedirectClientTargetClassifier {
  fun classify(redirectUri: String): OauthRedirectClientTarget {
    val uri = URI(redirectUri)
    val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
    val host = uri.host?.lowercase(Locale.ROOT)
    if ((scheme == "http" || scheme == "https") && (host == "127.0.0.1" || host == "localhost")) {
      return OauthRedirectClientTarget.LOOPBACK
    }
    if (scheme != "http" && scheme != "https") {
      return OauthRedirectClientTarget.APP_LINK
    }
    return OauthRedirectClientTarget.WEB
  }
}
