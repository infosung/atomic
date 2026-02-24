package com.infosung.atomic.spring.security.jwt

enum class TokenExpiredStatus {
  ACCESS_VALID,
  REFRESH_VALID,
  EXPIRED,
  INVALID,
}

enum class JwtClaimModel {
  USER_ID,
  SERVICE_NAME,
}

enum class JwtId {
  USER,
  GUEST,
}

enum class JwtSubject {
  USER_ID,
}
