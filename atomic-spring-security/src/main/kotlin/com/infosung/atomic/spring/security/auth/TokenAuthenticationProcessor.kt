package com.infosung.atomic.spring.security.auth

import com.infosung.atomic.spring.security.jwt.JwtProvider
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder

fun interface TokenAuthenticationProcessor {
  fun authenticate(token: String)
}

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
    log.debug("Authentication context updated: userId={}, subject={}", claims.id, claims.subject)
  }
}
