package com.infosung.atomic.crypto.envelope

import com.infosung.atomic.crypto.codec.Base64UrlCodec

object VersionedAeadEnvelopeCodec {
  fun encode(envelope: VersionedAeadEnvelope): String {
    val associatedData = envelope.associatedData?.let(Base64UrlCodec::encode).orEmpty()
    return listOf(
            "v${envelope.version}",
            Base64UrlCodec.encode(envelope.scheme),
            Base64UrlCodec.encode(envelope.keyId),
            Base64UrlCodec.encode(envelope.iv),
            Base64UrlCodec.encode(envelope.ciphertext),
            associatedData,
        )
        .joinToString(".")
  }

  fun decode(
      value: String,
      supportedVersions: Set<Int> = setOf(1),
  ): VersionedAeadEnvelope {
    val parts = value.split('.')
    require(parts.size == 6) { "Versioned AEAD envelope must contain six parts." }
    require(parts[0].startsWith("v")) { "Versioned AEAD envelope must start with vN." }
    val version =
        parts[0].removePrefix("v").toIntOrNull()
            ?: throw IllegalArgumentException("Versioned AEAD envelope version is invalid.")
    require(version in supportedVersions) { "Unsupported envelope version: $version" }

    return VersionedAeadEnvelope(
        version = version,
        scheme = Base64UrlCodec.decodeToString(parts[1]),
        keyId = Base64UrlCodec.decodeToString(parts[2]),
        iv = Base64UrlCodec.decode(parts[3]),
        ciphertext = Base64UrlCodec.decode(parts[4]),
        associatedData = parts[5].takeIf { it.isNotEmpty() }?.let(Base64UrlCodec::decode),
    )
  }
}
