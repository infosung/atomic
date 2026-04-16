package com.infosung.atomic.crypto

import com.infosung.atomic.crypto.envelope.AeadEnvelopeContext
import com.infosung.atomic.crypto.envelope.AeadEnvelopeKey
import com.infosung.atomic.crypto.envelope.AeadEnvelopeKeyResolver
import com.infosung.atomic.crypto.envelope.ContextBoundAeadEnvelopeCipher
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContextBoundAeadEnvelopeCipherTest {
  private val cipher = ContextBoundAeadEnvelopeCipher()

  @Test
  fun `context bound payload should round-trip with matching context`() {
    val key = AeadEnvelopeKey(keyId = "dek-v2", secret = testKey(1))
    val context =
        AeadEnvelopeContext(
            ownerId = "account-1",
            purpose = "sync-content",
            metadata =
                linkedMapOf(
                    "entityId" to "reflection-1",
                    "entityType" to "reflection",
                ),
        )

    val payload =
        cipher.encrypt(
            plaintext = "atomic".toByteArray(),
            key = key,
            keyVersion = 2,
            context = context,
        )

    val decrypted =
        cipher.decrypt(
            payload = payload,
            keyResolver =
                AeadEnvelopeKeyResolver { requestedKeyId ->
                  if (requestedKeyId == key.keyId) key.secret else null
                },
        )

    assertEquals(1, payload.cryptoVersion)
    assertEquals(2, payload.keyVersion)
    assertEquals(context, payload.context)
    assertContentEquals("atomic".toByteArray(), decrypted)
  }

  @Test
  fun `decrypt should fail when context metadata changes`() {
    val key = AeadEnvelopeKey(keyId = "dek-v2", secret = testKey(2))
    val payload =
        cipher.encrypt(
            plaintext = "atomic".toByteArray(),
            key = key,
            keyVersion = 2,
            context =
                AeadEnvelopeContext(
                    ownerId = "account-1",
                    purpose = "sync-content",
                    metadata = mapOf("entityId" to "reflection-1"),
                ),
        )

    val tampered =
        payload.copy(
            context =
                payload.context.copy(
                    metadata = mapOf("entityId" to "reflection-2"),
                ),
        )

    assertFailsWith<IllegalArgumentException> {
      cipher.decrypt(
          payload = tampered,
          keyResolver =
              AeadEnvelopeKeyResolver { requestedKeyId ->
                if (requestedKeyId == key.keyId) key.secret else null
              },
      )
    }
  }

  @Test
  fun `decrypt should resolve previous key by envelope key id`() {
    val previousKey = AeadEnvelopeKey(keyId = "dek-v1", secret = testKey(3))
    val payload =
        cipher.encrypt(
            plaintext = "atomic".toByteArray(),
            key = previousKey,
            keyVersion = 1,
            context =
                AeadEnvelopeContext(
                    ownerId = "account-1",
                    purpose = "sync-content",
                ),
        )

    val decrypted =
        cipher.decrypt(
            payload = payload,
            keyResolver =
                AeadEnvelopeKeyResolver { requestedKeyId ->
                  when (requestedKeyId) {
                    "dek-v1" -> previousKey.secret
                    else -> null
                  }
                },
        )

    assertContentEquals("atomic".toByteArray(), decrypted)
  }

  @Test
  fun `context canonicalization should ignore metadata insertion order`() {
    val key = AeadEnvelopeKey(keyId = "dek-v2", secret = testKey(4))
    val payload =
        cipher.encrypt(
            plaintext = "atomic".toByteArray(),
            key = key,
            keyVersion = 2,
            context =
                AeadEnvelopeContext(
                    ownerId = "account-1",
                    purpose = "sync-content",
                    metadata = linkedMapOf("b" to "2", "a" to "1"),
                ),
        )

    val reordered =
        payload.copy(
            context =
                payload.context.copy(
                    metadata = linkedMapOf("a" to "1", "b" to "2"),
                ),
        )

    val decrypted =
        cipher.decrypt(
            payload = reordered,
            keyResolver =
                AeadEnvelopeKeyResolver { requestedKeyId ->
                  if (requestedKeyId == key.keyId) key.secret else null
                },
        )

    assertContentEquals("atomic".toByteArray(), decrypted)
  }

  @Test
  fun `encrypt should snapshot mutable context metadata`() {
    val key = AeadEnvelopeKey(keyId = "dek-v2", secret = testKey(5))
    val metadata = linkedMapOf("entityId" to "reflection-1")

    val payload =
        cipher.encrypt(
            plaintext = "atomic".toByteArray(),
            key = key,
            keyVersion = 2,
            context =
                AeadEnvelopeContext(
                    ownerId = "account-1",
                    purpose = "sync-content",
                    metadata = metadata,
                ),
        )

    metadata["entityId"] = "reflection-2"

    val decrypted =
        cipher.decrypt(
            payload = payload,
            keyResolver =
                AeadEnvelopeKeyResolver { requestedKeyId ->
                  if (requestedKeyId == key.keyId) key.secret else null
                },
        )

    assertEquals("reflection-1", payload.context.metadata.getValue("entityId"))
    assertContentEquals("atomic".toByteArray(), decrypted)
  }

  @Test
  fun `key should defensively copy source secret bytes`() {
    val source = testKey(6)
    val key = AeadEnvelopeKey(keyId = "dek-v2", secret = source)
    source.fill(0)

    val payload =
        cipher.encrypt(
            plaintext = "atomic".toByteArray(),
            key = key,
            keyVersion = 2,
            context =
                AeadEnvelopeContext(
                    ownerId = "account-1",
                    purpose = "sync-content",
                ),
        )

    val decrypted =
        cipher.decrypt(
            payload = payload,
            keyResolver =
                AeadEnvelopeKeyResolver { requestedKeyId ->
                  if (requestedKeyId == key.keyId) key.secret else null
                },
        )

    assertContentEquals("atomic".toByteArray(), decrypted)
  }

  @Test
  fun `encrypt should reject non positive key version`() {
    assertFailsWith<IllegalArgumentException> {
      cipher.encrypt(
          plaintext = "atomic".toByteArray(),
          key = AeadEnvelopeKey(keyId = "dek-v2", secret = testKey(7)),
          keyVersion = 0,
          context =
              AeadEnvelopeContext(
                  ownerId = "account-1",
                  purpose = "sync-content",
              ),
      )
    }
  }

  private fun testKey(seed: Int): ByteArray = ByteArray(32) { index -> (seed + index).toByte() }
}
