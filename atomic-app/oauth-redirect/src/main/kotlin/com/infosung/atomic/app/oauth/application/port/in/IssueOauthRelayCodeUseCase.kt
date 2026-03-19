package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.OauthRelayPayload

internal fun interface IssueOauthRelayCodeUseCase {
  fun issue(payload: OauthRelayPayload): String
}
