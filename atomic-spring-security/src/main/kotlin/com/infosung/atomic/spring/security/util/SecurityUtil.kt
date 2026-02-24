package com.infosung.atomic.spring.security.util

import com.infosung.atomic.contract.exception.HttpUnauthorizedException
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.jwt.JwtDto
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User

data class SecurityCookiePolicy(
    val sameSite: String = "Strict",
    val secure: Boolean = true,
    val path: String = "/",
    val domain: String? = null,
)

object SecurityUtil {
  val DEFAULT_COOKIE_POLICY = SecurityCookiePolicy()

  fun userIdOrNull(): Long? {
    val user = SecurityContextHolder.getContext().authentication?.principal
    if (user == null || user !is User) {
      return null
    }
    return user.username.toLongOrNull()
  }

  fun userId(): Long = userIdOrNull() ?: throw HttpUnauthorizedException()

  fun tokenInHttpOnlyCookie(
      jwtDto: JwtDto,
      cookiePolicy: SecurityCookiePolicy = DEFAULT_COOKIE_POLICY,
      timeProvider: TimeProvider = TimeProvider(),
  ): HttpHeaders {
    val nowMillis = timeProvider.nowMillis()
    val accessMaxAgeSeconds = ((jwtDto.accessExpiredTime - nowMillis) / 1000 - 10).coerceAtLeast(0)
    val refreshMaxAgeSeconds =
        ((jwtDto.refreshExpiredTime - nowMillis) / 1000 - 10).coerceAtLeast(0)

    val accessCookie =
        buildCookie(
            name = "accessToken",
            value = jwtDto.accessToken,
            maxAgeSeconds = accessMaxAgeSeconds,
            cookiePolicy = cookiePolicy,
        )
    val refreshCookie =
        buildCookie(
            name = "refreshToken",
            value = jwtDto.refreshToken,
            maxAgeSeconds = refreshMaxAgeSeconds,
            cookiePolicy = cookiePolicy,
        )

    return HttpHeaders().apply {
      add(HttpHeaders.SET_COOKIE, accessCookie.toString())
      add(HttpHeaders.SET_COOKIE, refreshCookie.toString())
    }
  }

  fun removeHttpOnlyCookie(
      cookiePolicy: SecurityCookiePolicy = DEFAULT_COOKIE_POLICY
  ): HttpHeaders {
    val accessCookie =
        buildCookie(
            name = "accessToken",
            value = "",
            maxAgeSeconds = 0,
            cookiePolicy = cookiePolicy,
        )
    val refreshCookie =
        buildCookie(
            name = "refreshToken",
            value = "",
            maxAgeSeconds = 0,
            cookiePolicy = cookiePolicy,
        )

    return HttpHeaders().apply {
      add(HttpHeaders.SET_COOKIE, accessCookie.toString())
      add(HttpHeaders.SET_COOKIE, refreshCookie.toString())
    }
  }

  private fun buildCookie(
      name: String,
      value: String,
      maxAgeSeconds: Long,
      cookiePolicy: SecurityCookiePolicy,
  ): ResponseCookie {
    val builder =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookiePolicy.secure)
            .path(cookiePolicy.path)
            .sameSite(cookiePolicy.sameSite)
            .maxAge(maxAgeSeconds)

    cookiePolicy.domain?.takeIf { it.isNotBlank() }?.let { builder.domain(it) }
    return builder.build()
  }
}
