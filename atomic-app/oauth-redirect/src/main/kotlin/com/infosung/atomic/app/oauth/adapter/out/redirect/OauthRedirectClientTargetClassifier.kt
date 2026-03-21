package com.infosung.atomic.app.oauth.adapter.out.redirect

import java.net.URI
import java.util.Locale

enum class OauthRedirectClientTarget {
  WEB,
  APP_LINK,
  LOOPBACK,
}

internal object OauthRedirectClientTargetClassifier {
  fun classify(redirectUri: String): OauthRedirectClientTarget {
    val uri = URI(redirectUri)
    val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
    val host = uri.host?.lowercase(Locale.ROOT)
    return when {
      (scheme == "http" || scheme == "https") && (host == "127.0.0.1" || host == "localhost") ->
          OauthRedirectClientTarget.LOOPBACK
      scheme != "http" && scheme != "https" -> OauthRedirectClientTarget.APP_LINK
      else -> OauthRedirectClientTarget.WEB
    }
  }
}
