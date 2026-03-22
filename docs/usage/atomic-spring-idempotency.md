# atomic.spring.idempotency Guide

## Why Use This Module

Use `atomic.spring.idempotency` when your API needs one-time processing for retried requests.

Typical examples:

- order/payment creation
- reservation/booking creation
- external callback endpoints where clients retry on timeout

The module provides HTTP filter-based idempotency with replay response support.

## What It Provides

- `IdempotencyFilter`
- `IdempotencyStore` contract
- `IdempotencyFingerprintResolver` contract
- `InMemoryIdempotencyStore` default implementation
- `DefaultIdempotencyFingerprintResolver`

## Starter Auto-Configuration

When both conditions are true:

- dependency includes `atomic.spring.idempotency`
- `atomic.idempotency.enabled=true`

starter registers:

- `IdempotencyStore` (in-memory when missing)
- `IdempotencyFingerprintResolver` (default when missing)
- `IdempotencyFilter` + filter registration

## Properties

Full property index (default / required condition / description): [Property Reference by Module](environment-variables.md) -> `atomic.idempotency`

```yaml
atomic:
  idempotency:
    enabled: true
    header-name: Idempotency-Key
    ttl-seconds: 300
    processing-ttl-seconds: 3600
    require-header: true
    include-methods: [POST]
    fail-open: true
    replay-header-name: X-Idempotent-Replay
    replay-body-omitted-header-name: X-Idempotent-Replay-Body-Omitted
    max-cached-body-bytes: 262144
    in-memory:
      cleanup-interval: 1000
    filter:
      enabled: true
      order: -50
      url-patterns:
        - /*
```

## Runtime Behavior

1. Request arrives with `Idempotency-Key`.
2. Filter builds storage key with method/path/key.
3. Filter resolves request fingerprint.
4. Store claim result:
   - `Claimed`: proceed controller and cache response.
   - `Completed`: return cached response with `X-Idempotent-Replay=true`.
   - `Processing`: return `409`.
   - `FingerprintMismatch`: return `409`.
5. Response replay capture stores body up to `max-cached-body-bytes` only (hard capture limit).
6. If body exceeds the limit, replay stores status/headers only and adds `X-Idempotent-Replay-Body-Omitted=true`.
7. Non-5xx responses (including 4xx) are cached and replayed for the same key.
8. 5xx/exception path removes active key to allow retry.
9. With `fail-open=true`, store failures on `claim/complete/remove` are logged and request/response flow continues. On `complete` failure, filter also does best-effort `remove` to avoid long `Processing` lock.

Stable non-MVC error codes:

- `IDEMPOTENCY_KEY_REQUIRED`
  - emitted when `require-header=true` and `Idempotency-Key` is missing
  - HTTP status `400`
- `IDEMPOTENCY_REQUEST_PROCESSING`
  - emitted when another in-flight request already owns the key
  - HTTP status `409`
- `IDEMPOTENCY_FINGERPRINT_MISMATCH`
  - emitted when the same key is reused with a different fingerprint
  - HTTP status `409`

These rejection branches return JSON `BaseResponse` payloads instead of plain text bodies.

## Important Notes

- Default in-memory store is process-local.
- Multi-instance production services should provide shared `IdempotencyStore`.
- `processing-ttl-seconds` should be larger than maximum expected processing time.
- Default fingerprint resolver does not include raw request body.
- If body-aware matching is required, register custom `IdempotencyFingerprintResolver`.
- Replay excludes non-replayable headers such as `Set-Cookie`, hop-by-hop headers, and dynamic server headers.
- Starter validates idempotency properties at startup and fails fast on invalid values.

## Custom Store Example

```kotlin
import com.infosung.atomic.spring.idempotency.IdempotencyStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IdempotencyStoreConfig {
  @Bean
  fun idempotencyStore(): IdempotencyStore {
    // Implement with Redis/DB/shared cache for multi-instance deployments.
    throw NotImplementedError("Implement shared store")
  }
}
```

## Operational Checklist

- Ensure client sends stable `Idempotency-Key` per logical operation.
- Ensure `Idempotency-Key` is scoped per actor/tenant (for example `userId:uuid`) to avoid cross-client collisions.
- Set TTL according to retry window policy.
- Use shared store for multi-instance deployments.
- Monitor `409` ratio to detect duplicate retry spikes or misuse.
- Branch on stable idempotency codes instead of matching response text.
