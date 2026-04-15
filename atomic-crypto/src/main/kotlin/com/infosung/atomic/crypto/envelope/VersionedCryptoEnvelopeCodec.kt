package com.infosung.atomic.crypto.envelope

object VersionedCryptoEnvelopeCodec {
  fun encode(envelope: VersionedCryptoEnvelope): String =
      "v${envelope.version}.${envelope.algorithm}.${envelope.payload}"

  fun decode(value: String): VersionedCryptoEnvelope {
    val parts = value.split('.', limit = 3)
    require(parts.size == 3) { "Versioned crypto envelope must contain three parts." }
    require(parts[0].startsWith("v")) { "Versioned crypto envelope must start with vN." }
    val version =
        parts[0].removePrefix("v").toIntOrNull()
            ?: throw IllegalArgumentException("Versioned crypto envelope version is invalid.")
    return VersionedCryptoEnvelope(version = version, algorithm = parts[1], payload = parts[2])
  }
}
