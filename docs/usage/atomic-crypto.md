# atomic.crypto Guide

## Why Use This Module

Use `atomic.crypto` when you need Spring-free cryptographic primitives that stay reusable across
atomic modules and host applications.

It is intentionally small and dependency-light:

- no Spring Boot dependency
- no auto-configuration
- no runtime state machine
- only reusable crypto helpers

This module is a good fit when you want:

- HMAC-based signing and verification
- Base64Url codec helpers
- key derivation primitives
- AES-256-GCM envelope encryption
- versioned payload wrappers
- context-bound encrypted payload helpers for server-managed storage

## What It Provides

| Primitive | Responsibility |
|---|---|
| `Base64UrlCodec` | URL-safe Base64 encode/decode helpers without padding |
| `HmacSecretKeyRing` | current + previous signing key ring for rotation-aware verification |
| `HmacSigner` | HMAC message signing |
| `HmacVerifier` | HMAC signature verification across the key ring |
| `HkdfSha256` | HKDF-SHA256 key derivation |
| `Pbkdf2HmacSha256` | PBKDF2-HMAC-SHA256 key derivation |
| `AesGcmAead` | AES-256-GCM authenticated encryption/decryption |
| `VersionedAeadEnvelope` | structured envelope metadata |
| `VersionedAeadEnvelopeCodec` | text codec for versioned AEAD envelopes |
| `AeadEnvelopeContext` | owner/purpose/metadata context bound into associated data |
| `AeadEnvelopeKey` | immutable AES-256-GCM key holder with explicit `keyId` |
| `AeadEnvelopeKeyResolver` | key lookup seam used to resolve current or previous keys by `keyId` |
| `ContextBoundEncryptedPayload` | persisted payload contract containing `cryptoVersion`, `keyVersion`, context, fingerprint, and ciphertext envelope |
| `ContextBoundAeadEnvelopeCipher` | encrypt/decrypt helper that binds ciphertext to context and key id |

## Quick Start

```kotlin
import com.infosung.atomic.crypto.aead.AesGcmAead
import com.infosung.atomic.crypto.hmac.HmacAlgorithm
import com.infosung.atomic.crypto.hmac.HmacSigner
import com.infosung.atomic.crypto.kdf.HkdfSha256
import com.infosung.atomic.crypto.random.SecureRandoms

val signer = HmacSigner(HmacAlgorithm.HS512, "CHANGE_ME_WITH_A_STRONG_CURRENT_KEY".toByteArray())
val message = "atomic-crypto".toByteArray()
val signature = signer.sign(message)
check(signer.verify(message, signature))

val salt = SecureRandoms.nextBytes(16)
val derivedKey =
    HkdfSha256.derive(
        ikm = "ikm".toByteArray(),
        salt = salt,
        info = "example".toByteArray(),
        length = 32,
    )

val aead = AesGcmAead(derivedKey)
val encrypted = aead.encrypt(message)
val decrypted = aead.decrypt(encrypted)
check(message.contentEquals(decrypted))
```

Context-bound payload example:

```kotlin
import com.infosung.atomic.crypto.envelope.AeadEnvelopeContext
import com.infosung.atomic.crypto.envelope.AeadEnvelopeKey
import com.infosung.atomic.crypto.envelope.AeadEnvelopeKeyResolver
import com.infosung.atomic.crypto.envelope.ContextBoundAeadEnvelopeCipher

val cipher = ContextBoundAeadEnvelopeCipher()
val payload =
    cipher.encrypt(
        plaintext = "secret-body".toByteArray(),
        key = AeadEnvelopeKey(keyId = "dek-v2", secret = ByteArray(32) { 7 }),
        keyVersion = 2,
        context =
            AeadEnvelopeContext(
                ownerId = "account-1",
                purpose = "sync-content",
                metadata = mapOf("entityType" to "reflection", "entityId" to "r-1"),
            ),
    )

val plaintext =
    cipher.decrypt(
        payload = payload,
        keyResolver = AeadEnvelopeKeyResolver { keyId ->
          if (keyId == "dek-v2") ByteArray(32) { 7 } else null
        },
    )
check(String(plaintext) == "secret-body")
```

Important:
- `keyVersion` is part of the persisted payload contract. Keep it unchanged when you store or move
  `ContextBoundEncryptedPayload`, because decrypt binds it into associated data together with
  `ownerId`, `purpose`, and sorted metadata.

## Rotation Notes

- signing always uses the current key
- previous keys are verification-only
- keep the current and previous keys outside source control
- rotate one lane at a time so you can observe acceptance and rejection behavior
- if you use `atomic.spring.security`, you can wire the same rotation model into `JwtProvider`

## Usage Guidance

Use `atomic.crypto` directly when:

- you need envelope encryption for values outside JWT/security
- you need HMAC primitives in a host service without Spring
- you need deterministic key derivation for storage or relay payloads
- you want ciphertext to fail closed when owner/purpose/context metadata no longer matches

Use `ContextBoundAeadEnvelopeCipher` when your host app already owns:

- a current encryption key plus key identifier
- a stable owner scope such as `accountId` or `tenantId`
- plaintext metadata that should be bound into AEAD associated data

Keep these outside Atomic:

- KMS client integration
- wrapped-DEK persistence
- key lineage provisioning/rotation jobs
- re-encryption orchestration
- product-specific decrypt authorization rules

Do not use this module to model product state or workflow state.

If you need account binding, session issuance, replay logic, or provider selection rules, keep those
in the host app or in higher-level atomic modules.

## Dependency Setup

Published artifact example:

```kotlin
dependencies {
  implementation("com.infosung:atomic.crypto:0.2.0")
}
```

Local multi-module example:

```kotlin
dependencies {
  implementation(project(":atomic-crypto"))
}
```

## Operational Checklist

- use long random secrets
- keep previous keys only as long as the old tokens must remain valid
- write regression tests for round-trip, verification, and rotation behavior
- bind ciphertext to stable owner/purpose metadata when encrypting stored payloads
- never log raw keys or ciphertexts in production
