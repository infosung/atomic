package com.infosung.atomic.spring.security.jwt

/** Token state categories for [JwtDto.state]. */
enum class TokenExpiredStatus {
  ACCESS_VALID,
  REFRESH_VALID,
  EXPIRED,
  INVALID,
}

/** Standard JWT claim keys used by this module. */
enum class JwtClaimModel {
  USER_ID,
  SERVICE_NAME,
}

/** JWT identity namespace. */
enum class JwtId {
  USER,
  GUEST,
}

/** Standard JWT subject values used by this module. */
enum class JwtSubject {
  USER_ID,
}
