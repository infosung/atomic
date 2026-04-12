package com.infosung.atomic.app.oauth.adapter.out.relay.store

internal object OauthRelayCodeTableNamePolicy {
  const val DEFAULT_TABLE_NAME: String = "atomic_oauth_relay_code"

  fun validateOrThrow(raw: String): String {
    val candidate = raw.trim()
    require(candidate.matches(Regex("[A-Za-z0-9_]+"))) {
      "atomic.app.oauth.redirect.store.entity.table-name must contain only letters, numbers, and underscores."
    }
    return candidate
  }
}
