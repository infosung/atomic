package com.infosung.atomic.spring.security.auth

import io.jsonwebtoken.Claims
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User

fun interface JwtAuthenticationFactory {
  fun create(
      jwt: String,
      claims: Claims,
  ): Authentication
}

class JwtClaimsAuthenticationFactory : JwtAuthenticationFactory {
  override fun create(
      jwt: String,
      claims: Claims,
  ): Authentication {
    val grantedAuthority = createGrantedAuthorities(claims.subject)
    val user = User(claims.id.toString(), "", grantedAuthority)
    return UsernamePasswordAuthenticationToken(user, jwt, grantedAuthority)
  }

  private fun createGrantedAuthorities(role: String): Collection<GrantedAuthority> =
      listOf(SimpleGrantedAuthority("ROLE_$role"))
}
