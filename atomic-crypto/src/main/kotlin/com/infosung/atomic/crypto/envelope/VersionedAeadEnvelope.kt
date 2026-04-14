package com.infosung.atomic.crypto.envelope

data class VersionedAeadEnvelope(
    val version: Int,
    val scheme: String,
    val keyId: String,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val associatedData: ByteArray? = null,
) {
  init {
    require(version > 0) { "version must be greater than zero." }
    require(scheme.isNotBlank()) { "scheme must not be blank." }
    require(keyId.isNotBlank()) { "keyId must not be blank." }
    require(iv.isNotEmpty()) { "iv must not be empty." }
    require(ciphertext.isNotEmpty()) { "ciphertext must not be empty." }
  }
}
