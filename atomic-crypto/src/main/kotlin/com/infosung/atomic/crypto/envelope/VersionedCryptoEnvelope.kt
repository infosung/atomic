package com.infosung.atomic.crypto.envelope

data class VersionedCryptoEnvelope(
    val version: Int,
    val algorithm: String,
    val payload: String,
) {
  init {
    require(version > 0) { "version must be greater than zero." }
    require(algorithm.isNotBlank()) { "algorithm must not be blank." }
    require(payload.isNotBlank()) { "payload must not be blank." }
  }
}
