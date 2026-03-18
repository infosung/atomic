package com.infosung.atomic.app.oauth.adapter.out.relay

import com.infosung.atomic.app.oauth.AppOauthRelayCodeService
import com.infosung.atomic.app.oauth.OauthRelayPayload
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort

internal class AppOauthRelayCodePortAdapter(
    private val appOauthRelayCodeService: AppOauthRelayCodeService,
) : IssueOauthRelayCodePort {
  override fun issueRelayCode(payload: OauthRelayPayload): String {
    return appOauthRelayCodeService.issueRelayCode(payload)
  }
}
