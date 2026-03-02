package com.infosung.atomic.oauth.support

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Encodes query parameters and appends to [baseUrl]. */
internal fun encodeQuery(baseUrl: String, params: Map<String, String>): String {
  val query =
      params.entries.joinToString("&") { (key, value) ->
        "${encodeValue(key)}=${encodeValue(value)}"
      }
  return if (query.isEmpty()) baseUrl else "$baseUrl?$query"
}

/** URL-encodes single value using UTF-8. */
internal fun encodeValue(value: String): String {
  return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

/** Parses space-separated scopes into distinct set. */
internal fun parseScopes(rawScopes: String?): Set<String> {
  if (rawScopes.isNullOrBlank()) {
    return emptySet()
  }
  return rawScopes.split(' ').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
