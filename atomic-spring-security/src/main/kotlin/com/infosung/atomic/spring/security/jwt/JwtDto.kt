package com.infosung.atomic.spring.security.jwt

import com.infosung.atomic.contract.time.TimeProvider
import javax.crypto.SecretKey

data class JwtDto(
    val id: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiredTime: Long,
    val refreshExpiredTime: Long,
) {
  fun state(timeProvider: TimeProvider = TimeProvider()): TokenExpiredStatus {
    if (!isValidToken()) return TokenExpiredStatus.INVALID

    if (!isExpired(accessExpiredTime, timeProvider)) {
      return TokenExpiredStatus.ACCESS_VALID
    }

    if (!isExpired(refreshExpiredTime, timeProvider)) {
      return TokenExpiredStatus.REFRESH_VALID
    }

    return TokenExpiredStatus.EXPIRED
  }

  private fun isValidToken(): Boolean = isValidToken(accessToken) && isValidToken(refreshToken)

  private fun isValidToken(token: String): Boolean {
    if (token.isBlank()) return false
    var count = 0
    token.forEach { c -> if (c == '.') count++ }
    return count == 2
  }

  private fun isExpired(
      expiredTime: Long,
      timeProvider: TimeProvider,
  ): Boolean = expiredTime < timeProvider.nowMillis() - 10000
}

data class JwtProviderDto(
    val id: JwtId = JwtId.USER,
    val subject: JwtSubject = JwtSubject.USER_ID,
    val claims: Map<JwtClaimModel, String> = emptyMap(),
    val issuer: String? = null,
    val accessKey: SecretKey? = null,
    val refreshKey: SecretKey? = null,
    val accessExpiredSecond: Long? = null,
    val refreshExpiredSecond: Long? = null,
) {
  fun claims(
      userId: String,
      serviceName: String,
  ): JwtProviderDto =
      this.copy(
          claims =
              mapOf(
                  JwtClaimModel.USER_ID to userId,
                  JwtClaimModel.SERVICE_NAME to serviceName,
              ),
      )
}

data class IdTokenParseSimpleDto(
    val appleId: String? = null,
    val googleId: String? = null,
    val email: String,
    val appleRefreshToken: String? = null,
    val isLoginOnly: Boolean = false,
)

data class SimpleJwtDto(
    val id: String,
    val accessToken: String,
    val refreshToken: String,
)
