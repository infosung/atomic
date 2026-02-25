package com.infosung.atomic.spring.security.token

import com.infosung.atomic.contract.exception.HttpInvalidTokenException
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.jwt.JwtProvider
import com.infosung.atomic.spring.security.util.SecurityCookiePolicy
import com.infosung.atomic.spring.security.util.SecurityUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders

class RefreshTokenCookieIssuer(
    private val jwtProvider: JwtProvider,
    private val cookiePolicy: SecurityCookiePolicy = SecurityUtil.DEFAULT_COOKIE_POLICY,
    private val timeProvider: TimeProvider = TimeProvider(),
) {
  private val log = LoggerFactory.getLogger(RefreshTokenCookieIssuer::class.java)

  fun issueAccessTokenFromRefreshCookie(
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): String? {
    val refreshTokenCookie = "refreshToken"
    val refreshToken =
        getCookieValue(request, refreshTokenCookie)
            ?: run {
              log.trace("Refresh token cookie is missing")
              return null
            }
    log.debug("Refresh token cookie found. Attempting to issue access token.")
    return issueAccessToken(refreshToken, response)
  }

  fun issueAccessToken(
      refreshToken: String,
      response: HttpServletResponse,
  ): String {
    val claims = jwtProvider.getRefreshClaims(refreshToken)
    val subject = claims.subject ?: throw HttpInvalidTokenException("Token subject is missing.")
    val id = claims.id ?: throw HttpInvalidTokenException("Token id is missing.")
    log.debug("Issuing new access token from refresh token: subject={}", subject)
    val jwt =
        jwtProvider.createJwtDto(
            id = id,
            subject = subject,
        )

    val headers = SecurityUtil.tokenInHttpOnlyCookie(jwt, cookiePolicy, timeProvider)
    val cookieHeaders = headers.get(HttpHeaders.SET_COOKIE)
    cookieHeaders?.forEach { cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie) }
    log.trace("Set-Cookie headers added: count={}", cookieHeaders?.size ?: 0)

    return jwt.accessToken
  }

  private fun getCookieValue(
      request: HttpServletRequest,
      key: String,
  ): String? {
    request.cookies?.forEach { cookie ->
      if (key == cookie.name) {
        return cookie.value
      }
    }
    return null
  }
}
