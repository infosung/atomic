package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.StoreOauthRelayCodePort
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import java.security.SecureRandom
import java.util.Base64
import org.slf4j.LoggerFactory

internal class IssueOauthRelayCodeService(
    private val storeOauthRelayCodePort: StoreOauthRelayCodePort,
    private val properties: AtomicAppOauthRedirectProperties,
    private val timeProvider: TimeProvider = TimeProvider(),
) : IssueOauthRelayCodeUseCase {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val secureRandom = SecureRandom()

  override fun issue(payload: OauthRelayPayload): String {
    require(properties.relayCodeTtlSeconds > 0) {
      "atomic.app.oauth.redirect.relay-code-ttl-seconds must be greater than zero."
    }
    val relayCode = newRelayCode()
    val now = timeProvider.nowInstant()
    val expiresAt = now.plusSeconds(properties.relayCodeTtlSeconds)
    storeOauthRelayCodePort.save(
        relayCode = relayCode,
        payload = payload,
        expiresAt = expiresAt,
    )
    log.debug(
        "Issued oauth relayCode through application use-case: provider={}, relayCodeLength={}, ttlSeconds={}",
        payload.provider,
        relayCode.length,
        properties.relayCodeTtlSeconds,
    )
    return relayCode
  }

  private fun newRelayCode(): String {
    val bytes = ByteArray(24)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }
}
