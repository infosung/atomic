package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.domain.OauthRelayPayload

fun interface ConsumeOauthRelayCodeUseCase {
  fun consume(relayCode: String): OauthRelayPayload
}
