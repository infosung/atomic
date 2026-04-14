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

## What It Provides

| Primitive | Responsibility |
|---|---|
| `Base64UrlCodec` | URL-safe Base64 encode/decode helpers without padding |
| `HmacSecretKeyRing` | current + previous signing key ring for rotation-aware verification |
| `HmacSigner` | HMAC message signing |
| `HmacVerifier` | HMAC signature verification across the key ring |
| `HkdfSha256` | HKDF-SHA256 key derivation |
| `Pbkdf2HmacSha256` | PBKDF2-HMAC-SHA256 key derivation |
| `Aes256GcmCipher` | AES-256-GCM encryption/decryption |
| `VersionedCryptoEnvelope` | structured envelope metadata |
| `VersionedCryptoEnvelopeCodec` | text codec for versioned envelopes |

## Quick Start

```kotlin
import com.infosung.atomic.crypto.aead.Aes256GcmCipher
import com.infosung.atomic.crypto.hmac.HmacSigner
import com.infosung.atomic.crypto.hmac.HmacVerifier
import com.infosung.atomic.crypto.key.HmacSecretKeyRing

val ring =
    HmacSecretKeyRing(
        currentKey = "CHANGE_ME_WITH_A_STRONG_CURRENT_KEY",
        previousKeys = listOf("OPTIONAL_OLD_KEY"),
    )

val signer = HmacSigner(ring.algorithm, ring.currentSecretKey())
val verifier = HmacVerifier(ring.algorithm, ring.candidateSecretKeys())

val message = "atomic-crypto".toByteArray()
val signature = signer.sign(message)
check(verifier.verify(message, signature))

val cipher = Aes256GcmCipher()
val key = ByteArray(32) { index -> (index + 1).toByte() }
val encrypted = cipher.encryptToString(message, key)
val decrypted = cipher.decryptFromString(encrypted, key)
```

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

Do not use this module to model product state or workflow state.

If you need account binding, session issuance, replay logic, or provider selection rules, keep those
in the host app or in higher-level atomic modules.

## Dependency Setup

Published artifact example:

```kotlin
dependencies {
  implementation("com.infosung:atomic.crypto:0.1.3")
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
- never log raw keys or ciphertexts in production
