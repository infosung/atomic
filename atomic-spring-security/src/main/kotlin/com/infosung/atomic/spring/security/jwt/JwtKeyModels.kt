package com.infosung.atomic.spring.security.jwt

data class JwtSigningKey(
    val keyId: String,
    val secret: String,
) {
  init {
    require(keyId.isNotBlank()) { "keyId must not be blank." }
    require(secret.isNotBlank()) { "secret must not be blank." }
  }
}

data class JwtKeyRing(
    val active: JwtSigningKey,
    val previous: List<JwtSigningKey> = emptyList(),
) {
  init {
    val keyIds = (listOf(active) + previous).map(JwtSigningKey::keyId)
    require(keyIds.size == keyIds.distinct().size) { "JwtKeyRing key ids must be unique." }
  }
}
