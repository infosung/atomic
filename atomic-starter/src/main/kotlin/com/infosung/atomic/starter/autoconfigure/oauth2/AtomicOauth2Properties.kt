package com.infosung.atomic.starter.autoconfigure.oauth2

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties for atomic oauth2 auto-configuration. */
@ConfigurationProperties(prefix = "atomic.oauth2")
class AtomicOauth2Properties {
  /** Enables oauth2 auto-configuration. */
  var enabled: Boolean = true

  /** OAuth state token settings. */
  var state: State = State()

  /** OAuth provider auto-configuration settings. */
  var providers: Providers = Providers()

  class State {
    /** Enables state manager auto-configuration. */
    var enabled: Boolean = true

    /** Signing secret used for state token signature. */
    var signingSecret: String? = null

    /** State token issuer claim value. */
    var issuer: String = "atomic-oauth-state"

    /** State token ttl in seconds. */
    var ttlSeconds: Long = 300

    /** Maximum state attributes map entry count. */
    var maxAttributesEntryCount: Int = 10

    /** Maximum state attributes encoded byte size. */
    var maxAttributesBytes: Int = 512

    /** Maximum signed state token length. */
    var maxStateTokenLength: Int = 1200

    /** In-memory store settings. */
    var inMemoryStore: InMemoryStore = InMemoryStore()
  }

  class InMemoryStore {
    /** Enables in-memory one-time state store (default: false). */
    var enabled: Boolean = false

    /** Cleanup interval in operation counts. */
    var cleanupInterval: Int = 100
  }

  class Providers {
    /** Google provider settings. */
    var google: Google = Google()

    /** Kakao provider settings. */
    var kakao: Kakao = Kakao()

    /** Apple provider settings. */
    var apple: Apple = Apple()
  }

  class Google {
    /** Enables Google provider bean auto-registration. */
    var enabled: Boolean = false

    /** State attribute key used for client routing across OAuth flow. */
    var routeAttributeKey: String = "atomicClientKey"

    /** Default client key when request does not specify route key. */
    var defaultClientKey: String? = null

    /** OAuth client id. */
    var clientId: String? = null

    /** OAuth client secret. */
    var clientSecret: String? = null

    /** Server callback redirect URI registered at Google console. */
    var serverRedirectUri: String? = null

    /** OAuth grant type used in token exchange. */
    var authorizationGrantType: String = "authorization_code"

    /** Default scopes used when request scopes are empty. */
    var defaultScopes: MutableSet<String> = linkedSetOf("openid", "email", "profile")

    /** Optional allowed scopes whitelist. Empty means no additional whitelist constraint. */
    var supportedScopes: MutableSet<String> = linkedSetOf()

    /** User info endpoint URL. */
    var userInfoEndpoint: String = "https://openidconnect.googleapis.com/v1/userinfo"

    /** Allowed id-token audiences. Empty defaults to clientId. */
    var allowedAudiences: MutableSet<String> = linkedSetOf()

    /** Whether nonce is required when resolving identity from id token. */
    var requireNonceValidation: Boolean = false

    /** Allowed Google id-token issuers for verifier. */
    var verifierIssuers: MutableSet<String> =
        linkedSetOf("https://accounts.google.com", "accounts.google.com")

    /** Platform-specific Google client configurations keyed by client key. */
    var clients: MutableMap<String, GoogleClient> = linkedMapOf()
  }

  class GoogleClient {
    /** OAuth client id for this platform. */
    var clientId: String? = null

    /** OAuth client secret for this platform. */
    var clientSecret: String? = null

    /** Server callback redirect URI for this platform. */
    var serverRedirectUri: String? = null

    /** Allowed id-token audiences for this platform. Empty defaults to clientId. */
    var allowedAudiences: MutableSet<String> = linkedSetOf()
  }

  class Kakao {
    /** Enables Kakao provider bean auto-registration. */
    var enabled: Boolean = false

    /** State attribute key used for client routing across OAuth flow. */
    var routeAttributeKey: String = "atomicClientKey"

    /** Default client key when request does not specify route key. */
    var defaultClientKey: String? = null

    /** OAuth REST client id. */
    var clientId: String? = null

    /** Optional OAuth REST client secret. */
    var clientSecret: String? = null

    /** Server callback redirect URI registered at Kakao console. */
    var serverRedirectUri: String? = null

    /** Default scopes used when request scopes are empty. */
    var defaultScopes: MutableSet<String> = linkedSetOf("openid")

    /** Optional allowed scopes whitelist. Empty means no additional whitelist constraint. */
    var supportedScopes: MutableSet<String> = linkedSetOf()

    /** User info endpoint URL. */
    var userInfoEndpoint: String = "https://kapi.kakao.com/v1/oidc/userinfo"

    /** Whether nonce is required when resolving identity from id token. */
    var requireNonceValidation: Boolean = true

    /** Kakao id-token issuer for parser validation. */
    var idTokenIssuer: String = "https://kauth.kakao.com"

    /** Allowed Kakao id-token audiences. Empty defaults to clientId. */
    var idTokenAllowedAudiences: MutableSet<String> = linkedSetOf()

    /** Kakao OIDC JWK set URI. */
    var idTokenJwkSetUri: String = "https://kauth.kakao.com/.well-known/jwks.json"

    /** Platform-specific Kakao client configurations keyed by client key. */
    var clients: MutableMap<String, KakaoClient> = linkedMapOf()
  }

  class KakaoClient {
    /** OAuth client id for this platform. */
    var clientId: String? = null

    /** Optional OAuth client secret for this platform. */
    var clientSecret: String? = null

    /** Server callback redirect URI for this platform. */
    var serverRedirectUri: String? = null

    /** Allowed id-token audiences for this platform. Empty defaults to clientId. */
    var idTokenAllowedAudiences: MutableSet<String> = linkedSetOf()
  }

  class Apple {
    /** Enables Apple provider bean auto-registration. */
    var enabled: Boolean = false

    /** State attribute key used for client routing across OAuth flow. */
    var routeAttributeKey: String = "atomicClientKey"

    /** Default client key when request does not specify route key. */
    var defaultClientKey: String? = null

    /** Service id / client id registered in Apple developer console. */
    var clientId: String? = null

    /** Server callback redirect URI registered at Apple console. */
    var serverRedirectUri: String? = null

    /** Default scopes used when request scopes are empty. */
    var defaultScopes: MutableSet<String> = linkedSetOf("email")

    /** Whether nonce is required when resolving identity from id token. */
    var requireNonceValidation: Boolean = true

    /** Apple id-token issuer for parser validation. */
    var idTokenIssuer: String = "https://appleid.apple.com"

    /** Allowed Apple id-token audiences. Empty defaults to clientId. */
    var idTokenAllowedAudiences: MutableSet<String> = linkedSetOf()

    /** Apple OIDC JWK set URI. */
    var idTokenJwkSetUri: String = "https://appleid.apple.com/auth/keys"

    /** Platform-specific Apple client configurations keyed by client key. */
    var clients: MutableMap<String, AppleClient> = linkedMapOf()
  }

  class AppleClient {
    /** Client id for this platform. */
    var clientId: String? = null

    /** Server callback redirect URI for this platform. */
    var serverRedirectUri: String? = null

    /** Allowed id-token audiences for this platform. Empty defaults to clientId. */
    var idTokenAllowedAudiences: MutableSet<String> = linkedSetOf()
  }
}
