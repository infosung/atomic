package com.infosung.atomic.spring.security.token

import com.infosung.atomic.spring.security.channel.ClientChannel
import com.infosung.atomic.spring.security.channel.ClientChannelResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.util.StringUtils

fun interface RequestTokenResolver {
  fun resolve(
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): String?
}

class ChannelAwareTokenResolver(
    private val clientChannelResolver: ClientChannelResolver,
    private val refreshTokenCookieIssuer: RefreshTokenCookieIssuer,
) : RequestTokenResolver {
  private val log = LoggerFactory.getLogger(ChannelAwareTokenResolver::class.java)

  override fun resolve(
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): String? {
    val channel = clientChannelResolver.resolve(request)
    log.trace(
        "Resolving request token: channel={}, method={}, uri={}",
        channel,
        request.method,
        request.requestURI,
    )
    return when (channel) {
      ClientChannel.WEB -> {
        // WEB: HttpOnly Cookie only, Authorization header is ignored.
        log.debug("Token resolution strategy selected: WEB(cookie + refresh-cookie)")
        getTokenFromCookie(request)
            ?: refreshTokenCookieIssuer.issueAccessTokenFromRefreshCookie(request, response)
      }

      ClientChannel.APP -> {
        // APP: Authorization header only, cookies are ignored.
        log.debug("Token resolution strategy selected: APP(authorization header)")
        getTokenFromAuthorization(request)
      }

      ClientChannel.UNKNOWN -> {
        // Backward-compatible fallback path for requests that cannot be classified.
        log.debug("Token resolution strategy selected: UNKNOWN(fallback)")
        getTokenFromAuthorization(request)
            ?: getTokenFromCookie(request)
            ?: refreshTokenCookieIssuer.issueAccessTokenFromRefreshCookie(request, response)
      }
    }
  }

  private fun getTokenFromAuthorization(request: HttpServletRequest): String? {
    val bearerPrefix = "Bearer "
    val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
    log.trace("Authorization header is present={}", StringUtils.hasText(authorization))
    if (StringUtils.hasText(authorization) &&
        authorization.startsWith(bearerPrefix, ignoreCase = true)) {
      return authorization.substring(bearerPrefix.length).trim().takeIf { it.isNotBlank() }
    }
    return null
  }

  private fun getTokenFromCookie(request: HttpServletRequest): String? {
    val accessTokenCookie = "accessToken"
    request.cookies?.forEach { cookie ->
      if (accessTokenCookie == cookie.name) {
        log.trace("Access token cookie found")
        return cookie.value
      }
    }
    log.trace("Access token cookie not found")
    return null
  }
}
