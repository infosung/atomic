package com.infosung.atomic.app.oauth.application.port.out

import com.infosung.atomic.app.oauth.OauthRelayPayload
import java.time.Instant

internal interface StoreOauthRelayCodePort {
  fun save(
      relayCode: String,
      payload: OauthRelayPayload,
      expiresAt: Instant,
  )

  fun pop(
      relayCode: String,
      now: Instant,
  ): OauthRelayPayload?
}
