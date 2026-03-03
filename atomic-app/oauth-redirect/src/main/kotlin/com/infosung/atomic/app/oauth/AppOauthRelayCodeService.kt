package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.time.TimeProvider
import java.security.SecureRandom
import java.util.Base64
import org.slf4j.LoggerFactory

/** Issues and consumes one-time relay codes used by login API. */
class AppOauthRelayCodeService(
    private val relayCodeStore: OauthRelayCodeStore,
    private val properties: AtomicAppOauthRedirectProperties,
    private val timeProvider: TimeProvider = TimeProvider(),
) {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val secureRandom = SecureRandom()

  /**
   * Issues one-time relay code for OAuth callback payload.
   *
   * Relay code is stored server-side and expires by TTL.
   */
  fun issueRelayCode(payload: OauthRelayPayload): String {
    require(properties.relayCodeTtlSeconds > 0) {
      "atomic.app.oauth.redirect.relay-code-ttl-seconds must be greater than zero."
    }
    val relayCode = newRelayCode()
    val now = timeProvider.nowInstant()
    val expiresAt = now.plusSeconds(properties.relayCodeTtlSeconds)
    relayCodeStore.save(relayCode = relayCode, payload = payload, expiresAt = expiresAt)
    log.debug(
        "Issued oauth relayCode: provider={}, relayCodeLength={}, ttlSeconds={}",
        payload.provider,
        relayCode.length,
        properties.relayCodeTtlSeconds,
    )
    return relayCode
  }

  /**
   * Consumes relay code and returns stored OAuth payload once.
   *
   * @throws HttpStatusException 400 when relayCode is blank, unknown, expired, or already used.
   */
  fun consumeRelayCode(relayCode: String): OauthRelayPayload {
    val normalizedRelayCode = relayCode.trim()
    if (normalizedRelayCode.isBlank()) {
      throw HttpStatusException(status = 400, message = "relayCode is required.")
    }
    return relayCodeStore.pop(relayCode = normalizedRelayCode, now = timeProvider.nowInstant())
        ?: throw HttpStatusException(
            status = 400,
            message = "relayCode is invalid, expired, or already used.",
        )
  }

  private fun newRelayCode(): String {
    val bytes = ByteArray(24)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }
}
