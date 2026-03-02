package com.infosung.atomic.spring.security.auth

import com.infosung.atomic.contract.exception.HttpInvalidTokenException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.oauth2.jwt.Jwt

/** Factory for creating Spring [Authentication] from JWT claims. */
fun interface JwtAuthenticationFactory {
  /**
   * Builds authentication object from raw token and decoded claims.
   *
   * @throws HttpInvalidTokenException If required claims are missing.
   */
  fun create(
      jwt: String,
      claims: Jwt,
  ): Authentication
}

/** Default claims-to-authentication mapper. */
class JwtClaimsAuthenticationFactory : JwtAuthenticationFactory {
  override fun create(
      jwt: String,
      claims: Jwt,
  ): Authentication {
    val subject = claims.subject ?: throw HttpInvalidTokenException("Token subject is missing.")
    val id = claims.id ?: throw HttpInvalidTokenException("Token id is missing.")
    val grantedAuthority = createGrantedAuthorities(subject)
    val user = User(id, "", grantedAuthority)
    return UsernamePasswordAuthenticationToken(user, jwt, grantedAuthority)
  }

  private fun createGrantedAuthorities(role: String): Collection<GrantedAuthority> =
      listOf(SimpleGrantedAuthority("ROLE_$role"))
}
