package com.infosung.atomic.app.oauth.application.port.out

import com.infosung.atomic.app.oauth.OauthRelayPayload

internal interface IssueOauthRelayCodePort {
  fun issueRelayCode(payload: OauthRelayPayload): String
}
