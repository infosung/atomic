package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.application.exception.OauthRelayCodeApplicationException
import com.infosung.atomic.app.oauth.application.port.`in`.ConsumeOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.time.TimeProvider
import org.slf4j.LoggerFactory

/** Issues and consumes one-time relay codes used by login API. */
class AppOauthRelayCodeService
private constructor(
    private val issueOauthRelayCodeUseCase: IssueOauthRelayCodeUseCase,
    private val consumeOauthRelayCodeUseCase: ConsumeOauthRelayCodeUseCase,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  constructor(
      relayCodeStore: OauthRelayCodeStore,
      properties: AtomicAppOauthRedirectProperties,
      timeProvider: TimeProvider = TimeProvider(),
  ) : this(
      issueOauthRelayCodeUseCase =
          defaultIssueOauthRelayCodeUseCase(
              relayCodeStore = relayCodeStore,
              properties = properties,
              timeProvider = timeProvider,
          ),
      consumeOauthRelayCodeUseCase =
          defaultConsumeOauthRelayCodeUseCase(
              relayCodeStore = relayCodeStore,
              timeProvider = timeProvider,
          ),
  ) {
    log.debug(
        "Configured oauth relay facade with default application composition: relayCodeStoreType={}, ttlSeconds={}",
        relayCodeStore::class.java.name,
        properties.relayCodeTtlSeconds,
    )
  }

  internal constructor(
      relayCodeStore: OauthRelayCodeStore,
      properties: AtomicAppOauthRedirectProperties,
      issueOauthRelayCodeUseCase: IssueOauthRelayCodeUseCase,
      consumeOauthRelayCodeUseCase: ConsumeOauthRelayCodeUseCase,
  ) : this(
      issueOauthRelayCodeUseCase = issueOauthRelayCodeUseCase,
      consumeOauthRelayCodeUseCase = consumeOauthRelayCodeUseCase,
  ) {
    log.debug(
        "Configured oauth relay facade with injected application composition: relayCodeStoreType={}, ttlSeconds={}, issueUseCase={}, consumeUseCase={}",
        relayCodeStore::class.java.name,
        properties.relayCodeTtlSeconds,
        issueOauthRelayCodeUseCase::class.java.name,
        consumeOauthRelayCodeUseCase::class.java.name,
    )
  }

  /**
   * Issues one-time relay code for OAuth callback payload.
   *
   * Relay code is stored server-side and expires by TTL.
   */
  fun issueRelayCode(payload: OauthRelayPayload): String {
    val relayCode = issueOauthRelayCodeUseCase.issue(payload)
    log.debug(
        "Issued oauth relayCode through facade: provider={}, relayCodeLength={}",
        payload.provider,
        relayCode.length,
    )
    return relayCode
  }

  /**
   * Consumes relay code and returns stored OAuth payload once.
   *
   * @throws HttpStatusException 400 when relayCode is blank, unknown, expired, or already used.
   */
  fun consumeRelayCode(relayCode: String): OauthRelayPayload {
    return try {
      val payload = consumeOauthRelayCodeUseCase.consume(relayCode)
      log.debug(
          "Consumed oauth relayCode through facade: relayCodeLength={}, provider={}",
          relayCode.trim().length,
          payload.provider,
      )
      payload
    } catch (e: OauthRelayCodeApplicationException) {
      log.warn(
          "Rejected oauth relayCode consume through facade: relayCodeLength={}, message={}",
          relayCode.trim().length,
          e.message,
      )
      throw HttpStatusException(
          status = 400,
          message = e.message ?: "relayCode is invalid, expired, or already used.",
          cause = e,
      )
    }
  }

  companion object {
    private fun defaultIssueOauthRelayCodeUseCase(
        relayCodeStore: OauthRelayCodeStore,
        properties: AtomicAppOauthRedirectProperties,
        timeProvider: TimeProvider,
    ): IssueOauthRelayCodeUseCase {
      return OauthRelayCodeComposition.issueOauthRelayCodeUseCase(
          storeOauthRelayCodePort =
              OauthRelayCodeComposition.storeOauthRelayCodePort(relayCodeStore),
          properties = properties,
          timeProvider = timeProvider,
      )
    }

    private fun defaultConsumeOauthRelayCodeUseCase(
        relayCodeStore: OauthRelayCodeStore,
        timeProvider: TimeProvider,
    ): ConsumeOauthRelayCodeUseCase {
      return OauthRelayCodeComposition.consumeOauthRelayCodeUseCase(
          storeOauthRelayCodePort =
              OauthRelayCodeComposition.storeOauthRelayCodePort(relayCodeStore),
          timeProvider = timeProvider,
      )
    }
  }
}
