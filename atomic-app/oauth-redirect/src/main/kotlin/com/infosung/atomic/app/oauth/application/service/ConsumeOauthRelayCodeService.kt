package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.application.exception.OauthRedirectErrorCode
import com.infosung.atomic.app.oauth.application.exception.OauthRelayCodeRequestException
import com.infosung.atomic.app.oauth.application.port.`in`.ConsumeOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.StoreOauthRelayCodePort
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import org.slf4j.LoggerFactory

internal class ConsumeOauthRelayCodeService(
    private val storeOauthRelayCodePort: StoreOauthRelayCodePort,
    private val timeProvider: TimeProvider = TimeProvider(),
) : ConsumeOauthRelayCodeUseCase {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun consume(relayCode: String): OauthRelayPayload {
    val normalizedRelayCode = relayCode.trim()
    if (normalizedRelayCode.isBlank()) {
      throw OauthRelayCodeRequestException(
          message = "relayCode is required.",
          errorCode = OauthRedirectErrorCode.OAUTH_RELAY_CODE_REQUIRED,
      )
    }
    return storeOauthRelayCodePort.pop(
        relayCode = normalizedRelayCode,
        now = timeProvider.nowInstant(),
    )
        ?: run {
          log.debug(
              "Rejected oauth relayCode consume in application use-case: relayCodeLength={}, reason=missing_or_expired",
              normalizedRelayCode.length,
          )
          throw OauthRelayCodeRequestException("relayCode is invalid, expired, or already used.")
        }
  }
}
