package com.infosung.atomic.starter.autoconfigure.security

import com.infosung.atomic.spring.security.jwt.JwtProvider
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

    /** Active access token key id written into `kid`. */
    var accessKeyId: String = JwtProvider.DEFAULT_ACCESS_KEY_ID

    /** Optional previous access token verification keys keyed by `kid`. */
    var previousAccessKeys: MutableMap<String, String> = linkedMapOf()

    /** Refresh token signing key. */
    var refreshKey: String? = null

    /** Active refresh token key id written into `kid`. */
    var refreshKeyId: String = JwtProvider.DEFAULT_REFRESH_KEY_ID

    /** Optional previous refresh token verification keys keyed by `kid`. */
    var previousRefreshKeys: MutableMap<String, String> = linkedMapOf()

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
