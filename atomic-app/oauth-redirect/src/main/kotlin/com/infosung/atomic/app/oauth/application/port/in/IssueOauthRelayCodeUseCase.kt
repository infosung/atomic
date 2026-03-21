package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.domain.OauthRelayPayload

fun interface IssueOauthRelayCodeUseCase {
  fun issue(payload: OauthRelayPayload): String
}
