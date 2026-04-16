package com.infosung.atomic.crypto.envelope

import com.infosung.atomic.crypto.aead.AesGcmAead
import com.infosung.atomic.crypto.aead.AesGcmCiphertext
import com.infosung.atomic.crypto.codec.Base64UrlCodec
import com.infosung.atomic.crypto.random.SecureRandomSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class AeadEnvelopeContext(
    val ownerId: String,
    val purpose: String,
    val metadata: Map<String, String> = emptyMap(),
) {
  init {
    require(ownerId.isNotBlank()) { "ownerId must not be blank." }
    require(purpose.isNotBlank()) { "purpose must not be blank." }
    metadata.keys.forEach { key -> require(key.isNotBlank()) { "metadata key must not be blank." } }
  }
}

class AeadEnvelopeKey(
    val keyId: String,
    secret: ByteArray,
) {
  private val secretBytes = secret.copyOf()

  val secret: ByteArray
    get() = secretBytes.copyOf()

  init {
    require(keyId.isNotBlank()) { "keyId must not be blank." }
    require(secretBytes.size == KEY_SIZE_BYTES) { "AES-256-GCM key must be 32 bytes." }
  }

  companion object {
    private const val KEY_SIZE_BYTES = 32
  }
}

fun interface AeadEnvelopeKeyResolver {
  fun resolve(keyId: String): ByteArray?
}

data class ContextBoundEncryptedPayload(
    val cryptoVersion: Int = 1,
    val keyVersion: Int,
    val context: AeadEnvelopeContext,
    val contextFingerprint: String,
    val ciphertextEnvelope: String,
) {
  init {
    require(cryptoVersion > 0) { "cryptoVersion must be greater than zero." }
    require(keyVersion > 0) { "keyVersion must be greater than zero." }
    require(contextFingerprint.isNotBlank()) { "contextFingerprint must not be blank." }
    require(ciphertextEnvelope.isNotBlank()) { "ciphertextEnvelope must not be blank." }
  }
}

class ContextBoundAeadEnvelopeCipher(
    private val secureRandomSource: SecureRandomSource = SecureRandomSource(),
) {
  fun encrypt(
      plaintext: ByteArray,
      key: AeadEnvelopeKey,
      keyVersion: Int,
      context: AeadEnvelopeContext,
      cryptoVersion: Int = CRYPTO_VERSION,
  ): ContextBoundEncryptedPayload {
    require(keyVersion > 0) { "keyVersion must be greater than zero." }
    require(cryptoVersion > 0) { "cryptoVersion must be greater than zero." }

    val contextSnapshot = snapshotContext(context)
    val associatedData = encodeAssociatedData(cryptoVersion, keyVersion, contextSnapshot)
    val ciphertext = AesGcmAead(key.secret, secureRandomSource).encrypt(plaintext, associatedData)
    val envelope =
        VersionedAeadEnvelopeCodec.encode(
            VersionedAeadEnvelope(
                version = ENVELOPE_VERSION,
                scheme = ENVELOPE_SCHEME,
                keyId = key.keyId,
                iv = ciphertext.iv,
                ciphertext = ciphertext.ciphertext,
            ),
        )
    return ContextBoundEncryptedPayload(
        cryptoVersion = cryptoVersion,
        keyVersion = keyVersion,
        context = contextSnapshot,
        contextFingerprint = fingerprintAssociatedData(associatedData),
        ciphertextEnvelope = envelope,
    )
  }

  fun decrypt(
      payload: ContextBoundEncryptedPayload,
      keyResolver: AeadEnvelopeKeyResolver,
  ): ByteArray {
    val envelope = VersionedAeadEnvelopeCodec.decode(payload.ciphertextEnvelope)
    require(envelope.scheme == ENVELOPE_SCHEME) {
      "Unsupported envelope scheme: ${envelope.scheme}"
    }
    val contextSnapshot = snapshotContext(payload.context)
    val associatedData =
        encodeAssociatedData(payload.cryptoVersion, payload.keyVersion, contextSnapshot)
    require(payload.contextFingerprint == fingerprintAssociatedData(associatedData)) {
      "Stored contextFingerprint does not match the provided payload context."
    }
    envelope.associatedData?.let { storedAssociatedData ->
      require(storedAssociatedData.contentEquals(associatedData)) {
        "Stored associatedData does not match the provided payload context."
      }
    }
    val secret =
        keyResolver.resolve(envelope.keyId)?.copyOf()
            ?: throw IllegalArgumentException("No encryption key resolved for ${envelope.keyId}.")
    return AesGcmAead(secret)
        .decrypt(
            ciphertext =
                AesGcmCiphertext(
                    iv = envelope.iv,
                    ciphertext = envelope.ciphertext,
                ),
            associatedData = associatedData,
        )
  }

  private fun snapshotContext(context: AeadEnvelopeContext): AeadEnvelopeContext =
      AeadEnvelopeContext(
          ownerId = context.ownerId,
          purpose = context.purpose,
          metadata = context.metadata.toMap(),
      )

  private fun encodeAssociatedData(
      cryptoVersion: Int,
      keyVersion: Int,
      context: AeadEnvelopeContext,
  ): ByteArray {
    val components = buildList {
      add("v${ENVELOPE_VERSION}")
      add("crypto:${Base64UrlCodec.encode(cryptoVersion.toString())}")
      add("key:${Base64UrlCodec.encode(keyVersion.toString())}")
      add("owner:${Base64UrlCodec.encode(context.ownerId)}")
      add("purpose:${Base64UrlCodec.encode(context.purpose)}")
      context.metadata.toSortedMap().forEach { (key, value) ->
        add(
            "meta:${Base64UrlCodec.encode(key)}=${Base64UrlCodec.encode(value)}",
        )
      }
    }
    return components.joinToString("|").toByteArray(StandardCharsets.UTF_8)
  }

  private fun fingerprintAssociatedData(associatedData: ByteArray): String =
      Base64UrlCodec.encode(
          MessageDigest.getInstance("SHA-256").digest(associatedData),
      )

  companion object {
    private const val CRYPTO_VERSION = 1
    private const val ENVELOPE_VERSION = 1
    private const val ENVELOPE_SCHEME = "AES-256-GCM"
  }
}
