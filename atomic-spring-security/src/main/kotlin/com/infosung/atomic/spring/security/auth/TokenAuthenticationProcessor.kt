package com.infosung.atomic.spring.security.auth

import com.infosung.atomic.spring.security.jwt.JwtProvider
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Performs authentication side-effects (typically populating security context).
 */
fun interface TokenAuthenticationProcessor {
  /**
   * Authenticates [token] and updates current security context.
   */
  fun authenticate(token: String)
}

/**
 * JWT-backed [TokenAuthenticationProcessor].
 */
class JwtTokenAuthenticationProcessor(
    private val jwtProvider: JwtProvider,
    private val authenticationFactory: JwtAuthenticationFactory,
) : TokenAuthenticationProcessor {
  private val log = LoggerFactory.getLogger(JwtTokenAuthenticationProcessor::class.java)

  override fun authenticate(token: String) {
    log.trace("Authenticating token: len={}", token.length)
    val claims = jwtProvider.getAccessClaims(token)
    val authentication = authenticationFactory.create(token, claims)
    SecurityContextHolder.getContext().authentication = authentication
    log.debug(
        "Authentication context updated: userId={}, subject={}",
        claims.id ?: "unknown",
        claims.subject ?: "unknown",
    )
  }
}
