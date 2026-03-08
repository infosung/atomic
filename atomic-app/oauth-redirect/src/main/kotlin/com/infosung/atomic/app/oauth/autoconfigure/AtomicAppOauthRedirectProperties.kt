package com.infosung.atomic.app.oauth.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/** Properties for app-level OAuth redirect/callback relay API. */
@ConfigurationProperties(prefix = "atomic.app.oauth.redirect")
class AtomicAppOauthRedirectProperties {
  /** Enables OAuth redirect/callback relay API. */
  var enabled: Boolean = false

  /** HTTP endpoint base path for redirect endpoint. */
  var redirectEndpointPath: String = "/oauth/redirect"

  /**
   * HTTP endpoint base path for callback endpoints.
   *
   * Final callback paths are:
   * - `{callbackEndpointPath}/{provider}` for Google/Kakao style callbacks
   * - `{callbackEndpointPath}/apple` for Apple form_post callback
   */
  var callbackEndpointPath: String = "/oauth/callback"

  /** Query parameter key appended to frontend redirect URL. */
  var relayCodeQueryParameterName: String = "relayCode"

  /** Relay code TTL seconds. */
  var relayCodeTtlSeconds: Long = 300

  /**
   * Allowed redirect URI patterns.
   *
   * Match is validated by scheme, host, port, and path prefix boundary (not raw string startsWith).
   * Must not be empty when redirect API is enabled.
   */
  var allowedRedirectUriPrefixes: List<String> = emptyList()

  /** Callback request-binding options for OAuth redirect/callback anti-CSRF check. */
  var callbackBinding: CallbackBinding = CallbackBinding()

  /** Relay code store settings. */
  var store: Store = Store()

  class Store {
    /** Relay store backend type. */
    var type: StoreType = StoreType.ENTITY

    /**
     * When true, startup fails if selected store dependencies are not available.
     *
     * When false, falls back to in-memory store (process-local).
     */
    var failFast: Boolean = true

    /** In-memory store options. */
    var inMemory: InMemory = InMemory()

    /** Cache store options (Redis, Caffeine, etc via CacheManager). */
    var cache: Cache = Cache()

    /** Entity store options (RDB table based). */
    var entity: Entity = Entity()
  }

  class InMemory {
    /**
     * Cleanup interval in operation count.
     *
     * When less than or equal to zero, periodic expired-entry cleanup is disabled.
     */
    var cleanupInterval: Int = 100
  }

  class Cache {
    /** Cache name used by CacheManager. */
    var cacheName: String = "atomicOauthRelayCode"

    /** Relay cache key prefix. */
    var keyPrefix: String = "atomic:oauth:relay:"

    /**
     * Optional TTL override in seconds. Null uses relayCodeTtlSeconds. Must be greater than zero.
     */
    var ttlSeconds: Long? = null
  }

  class Entity {
    /** Table name for relay records. Only letters, numbers, and underscores are allowed. */
    var tableName: String = "atomic_oauth_relay_code"
  }

  class CallbackBinding {
    /** Enables callback binding verification using state attributes + cookie token match. */
    var enabled: Boolean = true

    /** State attribute key used to store callback binding token. */
    var stateAttributeKey: String = "atomicCallbackBinding"

    /** Cookie name used to persist callback binding token between redirect and callback. */
    var cookieName: String = "__Host-atomic_oauth_callback_binding"

    /** Cookie SameSite policy (`None` recommended for provider callback compatibility). */
    var cookieSameSite: String = "None"

    /** Cookie path for callback binding token. */
    var cookiePath: String = "/"

    /** Secure cookie flag for callback binding token. */
    var cookieSecure: Boolean = true

    /** Max age in seconds for callback binding cookie. */
    var cookieMaxAgeSeconds: Long = 600
  }

  enum class StoreType {
    IN_MEMORY,
    CACHE,
    ENTITY,
  }
}
