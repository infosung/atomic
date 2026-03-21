package com.infosung.atomic.app.oauth.adapter.out.relay

import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.application.port.out.StoreOauthRelayCodePort
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import java.time.Instant

internal class OauthRelayCodeStorePortAdapter(
    private val oauthRelayCodeStore: OauthRelayCodeStore,
) : StoreOauthRelayCodePort {
  override fun save(relayCode: String, payload: OauthRelayPayload, expiresAt: Instant) {
    oauthRelayCodeStore.save(
        relayCode = relayCode,
        payload = payload,
        expiresAt = expiresAt,
    )
  }

  override fun pop(relayCode: String, now: Instant): OauthRelayPayload? {
    return oauthRelayCodeStore.pop(relayCode = relayCode, now = now)
  }
}
