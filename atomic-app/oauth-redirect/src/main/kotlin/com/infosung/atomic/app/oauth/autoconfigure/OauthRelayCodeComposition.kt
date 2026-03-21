package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.adapter.out.relay.OauthRelayCodeStorePortAdapter
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.application.port.`in`.ConsumeOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.StoreOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.service.ConsumeOauthRelayCodeService
import com.infosung.atomic.app.oauth.application.service.IssueOauthRelayCodeService
import com.infosung.atomic.contract.time.TimeProvider

internal object OauthRelayCodeComposition {
  fun storeOauthRelayCodePort(
      oauthRelayCodeStore: OauthRelayCodeStore,
  ): StoreOauthRelayCodePort {
    return OauthRelayCodeStorePortAdapter(oauthRelayCodeStore)
  }

  fun issueOauthRelayCodeUseCase(
      storeOauthRelayCodePort: StoreOauthRelayCodePort,
      properties: AtomicAppOauthRedirectProperties,
      timeProvider: TimeProvider,
  ): IssueOauthRelayCodeUseCase {
    return IssueOauthRelayCodeService(
        storeOauthRelayCodePort = storeOauthRelayCodePort,
        properties = properties,
        timeProvider = timeProvider,
    )
  }

  fun consumeOauthRelayCodeUseCase(
      storeOauthRelayCodePort: StoreOauthRelayCodePort,
      timeProvider: TimeProvider,
  ): ConsumeOauthRelayCodeUseCase {
    return ConsumeOauthRelayCodeService(
        storeOauthRelayCodePort = storeOauthRelayCodePort,
        timeProvider = timeProvider,
    )
  }
}
