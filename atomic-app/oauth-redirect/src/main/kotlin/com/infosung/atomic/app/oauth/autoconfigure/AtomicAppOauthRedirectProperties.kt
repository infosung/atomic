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
   * If empty, any redirectUri is allowed. Configure for production.
   */
  var allowedRedirectUriPrefixes: List<String> = emptyList()

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

  enum class StoreType {
    IN_MEMORY,
    CACHE,
    ENTITY,
  }
}
