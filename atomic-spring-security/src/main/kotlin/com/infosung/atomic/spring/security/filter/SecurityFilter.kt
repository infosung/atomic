package com.infosung.atomic.spring.security.filter

import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.auth.JwtAuthenticationFactory
import com.infosung.atomic.spring.security.auth.JwtClaimsAuthenticationFactory
import com.infosung.atomic.spring.security.auth.JwtTokenAuthenticationProcessor
import com.infosung.atomic.spring.security.auth.TokenAuthenticationProcessor
import com.infosung.atomic.spring.security.channel.ClientChannelResolver
import com.infosung.atomic.spring.security.channel.DefaultClientChannelResolver
import com.infosung.atomic.spring.security.jwt.JwtProvider
import com.infosung.atomic.spring.security.token.ChannelAwareTokenResolver
import com.infosung.atomic.spring.security.token.RefreshTokenCookieIssuer
import com.infosung.atomic.spring.security.token.RequestTokenResolver
import com.infosung.atomic.spring.security.util.SecurityCookiePolicy
import com.infosung.atomic.spring.security.util.SecurityUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

/**
 * JWT authentication servlet filter.
 *
 * Responsibilities:
 * - Skip configured excluded endpoints.
 * - Resolve token from request channel strategy.
 * - Populate Spring Security context on success.
 * - Return 401 JSON response on authentication failure.
 */
class SecurityFilter(
    private val jwtProvider: JwtProvider,
    private val objectMapper: ObjectMapper,
    private val excludeUrls: List<String>,
    private val clientChannelResolver: ClientChannelResolver = DefaultClientChannelResolver(),
    private val cookiePolicy: SecurityCookiePolicy = SecurityUtil.DEFAULT_COOKIE_POLICY,
    private val timeProvider: TimeProvider = TimeProvider(),
) : OncePerRequestFilter() {
  private val log: Logger = LoggerFactory.getLogger(SecurityFilter::class.java)
  private val refreshTokenCookieIssuer: RefreshTokenCookieIssuer =
      RefreshTokenCookieIssuer(jwtProvider, cookiePolicy, timeProvider)
  private val tokenResolver: RequestTokenResolver =
      ChannelAwareTokenResolver(clientChannelResolver, refreshTokenCookieIssuer)
  private val authenticationFactory: JwtAuthenticationFactory = JwtClaimsAuthenticationFactory()
  private val tokenAuthenticationProcessor: TokenAuthenticationProcessor =
      JwtTokenAuthenticationProcessor(jwtProvider, authenticationFactory)

  /** Performs token resolution/authentication for each request once. */
  override fun doFilterInternal(
      request: HttpServletRequest,
      response: HttpServletResponse,
      filterChain: FilterChain,
  ) {
    val uri = "${request.method} ${request.requestURI}"
    log.debug("Security filter invoked: {}", uri)

    if (excludeUrls.contains(uri)) {
      log.trace("Bypassing security filter for excluded path: {}", uri)
      filterChain.doFilter(request, response)
      return
    }

    val token = tokenResolver.resolve(request, response)

    if (token.isNullOrBlank()) {
      log.trace("No token resolved for request: {}", uri)
      filterChain.doFilter(request, response)
      return
    }

    try {
      tokenAuthenticationProcessor.authenticate(token)
      log.debug("Authentication context has been set: {}", uri)
      filterChain.doFilter(request, response)
    } catch (e: Exception) {
      log.warn("Authentication failed for request: {}", uri, e)
      response.status = HttpServletResponse.SC_UNAUTHORIZED
      response.writer.print(
          objectMapper.writeValueAsString(
              BaseResponse<Any>(
                  code = HttpStatus.UNAUTHORIZED.name,
                  message = HttpStatus.UNAUTHORIZED.name,
              ),
          ),
      )
      response.contentType = MediaType.APPLICATION_JSON.toString()
    }
  }
}
