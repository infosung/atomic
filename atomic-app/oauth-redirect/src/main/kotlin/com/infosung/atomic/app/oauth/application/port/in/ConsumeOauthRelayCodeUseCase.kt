package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.OauthRelayPayload

internal fun interface ConsumeOauthRelayCodeUseCase {
  fun consume(relayCode: String): OauthRelayPayload
}
