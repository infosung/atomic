package com.infosung.atomic.app.oauth.adapter.out.relay

import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload

internal class IssueOauthRelayCodeUseCasePortAdapter(
    private val issueOauthRelayCodeUseCase: IssueOauthRelayCodeUseCase,
) : IssueOauthRelayCodePort {
  override fun issueRelayCode(payload: OauthRelayPayload): String {
    return issueOauthRelayCodeUseCase.issue(payload)
  }
}
