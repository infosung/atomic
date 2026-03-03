package com.infosung.atomic.starter.autoconfigure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties for atomic spring-security auto-configuration. */
@ConfigurationProperties(prefix = "atomic.security")
class AtomicSecurityProperties {
  /** Enables atomic security auto-configuration. */
  var enabled: Boolean = true

  /** Excluded request patterns in `METHOD /path` format. */
  var excludeUrls: MutableList<String> = mutableListOf()

  /** JWT provider settings. */
  var jwt: Jwt = Jwt()

  /** Cookie policy settings. */
  var cookie: Cookie = Cookie()

  class Jwt {
    /**
     * Enables JwtProvider auto-registration.
     *
     * Note: JwtSecurityConfigurerAdapter still requires JwtProvider (auto or custom) when
     * `atomic.security.enabled=true`.
     */
    var enabled: Boolean = true

    /** Access token signing key. */
    var accessKey: String? = null

    /** Refresh token signing key. */
    var refreshKey: String? = null

    /** JCA HMAC algorithm name. */
    var algorithm: String = "HmacSHA512"

    /** Issuer/service name claim value. */
    var serviceName: String = "InfosungAtomic"

    /** Access token expiration in seconds. */
    var accessExpiredSecond: Long = 60L * 60L

    /** Refresh token expiration in seconds. */
    var refreshExpiredSecond: Long = 60L * 60L * 24L * 14L
  }

  class Cookie {
    /** SameSite policy. */
    var sameSite: String = "Strict"

    /** Secure cookie flag. */
    var secure: Boolean = true

    /** Cookie path. */
    var path: String = "/"

    /** Optional cookie domain. */
    var domain: String? = null
  }
}
