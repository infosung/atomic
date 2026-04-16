# atomic.contract Guide

## Why Use This Module

Use `atomic.contract` when you want one shared contract across API, web, security, and batch layers.

It provides:

- common response models (`BaseResponse`, `CursorPage`, `OffsetPage`)
- common header models/constants (`ApiHeaderDto`, `ApiHeaderNames`, `TraceIdGenerator`)
- common domain-level exceptions (`HttpStatusException` and token-related subclasses)
- bootstrap/readiness DTOs for startup or sync-gate contracts
- simple utilities (`TimeProvider`, `DateUtil`, `RandomUtil`, `BasicAuthHeader`, `IpMasker`)

## Quick Start (10 Minutes)

1. Return controller responses with `BaseResponse<T>`.
2. Use `HttpStatusException` for wire-level HTTP errors.
3. Use `CursorPage` or `OffsetPage` for list endpoints.
4. Reuse `BootstrapReadinessResponse` when your app exposes a startup/readiness contract.
5. Add `TimeProvider` bean only if you need controllable clock/timezone.

```kotlin
import com.infosung.atomic.contract.response.BaseResponse

fun health(): BaseResponse<Map<String, String>> =
    BaseResponse.ok(mapOf("status" to "ok"))
```

## What You Usually Use First

### 1) API Response Model

`BaseResponse<T>` is the default response envelope.

```kotlin
import com.infosung.atomic.contract.response.BaseResponse

fun success(): BaseResponse<Map<String, Any>> =
    BaseResponse.ok(mapOf("id" to 1L, "name" to "demo"))

fun failure(e: Exception): BaseResponse<Any> = BaseResponse.error(e)
```

### 2) Pagination Models

Use one model per endpoint contract.

- `CursorPage<T>`: cursor-based APIs
- `OffsetPage<T>`: page/size-based APIs

```kotlin
import com.infosung.atomic.contract.response.CursorPage
import com.infosung.atomic.contract.response.OffsetPage

val cursor = CursorPage(list = listOf("a", "b"), hasNext = true, cursor = "next-cursor")
val offset = OffsetPage.build(list = listOf("a", "b"), totalSize = 100, currentPage = 0, size = 20)
```

### 3) Header Contract

Use `ApiHeaderDto` and `ApiHeaderNames` as shared header schema across modules.

```kotlin
import com.infosung.atomic.contract.header.ApiHeaderNames

val traceHeaderName = ApiHeaderNames.HEADER_X_TRACE_ID
```

## Utilities You Can Reuse

- `TimeProvider`: controllable clock/timezone source (useful for auth expiry logic and tests)
- `DateUtil.plusTime(...)`: date arithmetic with explicit timezone option
- `RandomUtil.randomString(...)`: secure random string generation
- `BasicAuthHeader.create(...)`: Basic auth header string builder
- `IpMasker.mask(...)`: IP masking helper for logging/privacy

## Bootstrap Readiness Contract

Use the bootstrap/readiness models when you want one stable DTO for a read-only startup gate endpoint,
without forcing Atomic to own your session, device, or bootstrap state machine.

What Atomic gives you here:

- response DTO shape (`BootstrapReadinessResponse`)
- authoritative enums (`BootstrapReadinessState`, `BootstrapReadinessReasonCode`, `BootstrapReadinessNextAction`)
- light validation that prevents inconsistent `READY` / `RESET_REQUIRED` combinations

What stays in your app:

- the actual `/bootstrap/readiness` endpoint
- session validation and device authorization
- reason-code precedence rules
- reset/merge/bootstrap execution

Capability keys should be stable namespaced identifiers owned by your app team, for example
`bootstrap.push-hint-enabled` or `sync.websocket-hint-enabled`.

```kotlin
import com.infosung.atomic.contract.bootstrap.BootstrapCapabilitySet
import com.infosung.atomic.contract.bootstrap.BootstrapReadiness
import com.infosung.atomic.contract.bootstrap.BootstrapReadinessAccount
import com.infosung.atomic.contract.bootstrap.BootstrapReadinessDevice
import com.infosung.atomic.contract.bootstrap.BootstrapReadinessDeviceState
import com.infosung.atomic.contract.bootstrap.BootstrapReadinessResponse
import com.infosung.atomic.contract.bootstrap.BootstrapReadinessServer
import com.infosung.atomic.contract.bootstrap.BootstrapReadinessSession
import com.infosung.atomic.contract.bootstrap.BootstrapReadinessSessionState
import java.time.Instant

fun readiness(): BootstrapReadinessResponse =
    BootstrapReadinessResponse(
        account = BootstrapReadinessAccount(accountId = "account-1"),
        session =
            BootstrapReadinessSession(
                sessionState = BootstrapReadinessSessionState.ACTIVE,
                accessTokenExpiresAt = Instant.parse("2026-04-15T12:34:56Z"),
                refreshInactivityExpiresAt = Instant.parse("2026-05-15T12:34:56Z"),
                refreshAbsoluteExpiresAt = Instant.parse("2026-10-12T12:34:56Z"),
            ),
        device =
            BootstrapReadinessDevice(
                deviceId = "device-1",
                deviceState = BootstrapReadinessDeviceState.ACTIVE,
            ),
        bootstrap = BootstrapReadiness.ready(latestSnapshotExists = true, serverHeadSeq = 1234),
        capabilities = BootstrapCapabilitySet(mapOf("bootstrap.push-hint-enabled" to true)),
        server = BootstrapReadinessServer(serverTime = Instant.now()),
    )
```

## Exception Contract

Use `HttpStatusException` as the shared wire-level error contract between web adapters and global HTTP exception handling.

- `HttpStatusException(status, message, cause, code?)`
- `HttpInvalidTokenException` (401)
- `HttpUnauthorizedException` (401)
- `HttpTokenNotExpiredException` (400)

Important separation:

- application/domain exceptions do not need to extend `HttpStatusException`
- web adapters or shared exception mappers convert typed module failures into `HttpStatusException`
- the stable wire identity should come from `HttpStatusException.code`, not from exception simple name parsing

These are commonly translated by `atomic.spring.web`.

## Bean Registration

`atomic.contract` itself does not require bean registration.

Optional bean registration only when you need DI-managed behavior:

- `TimeProvider`
- `TraceIdGenerator`

## Operational Checklist

- Keep one response envelope rule (`BaseResponse`) for all APIs.
- Pick one pagination model per endpoint and keep it fixed.
- Keep bootstrap/readiness as a read-only contract when you reuse these models.
- Use `TimeProvider` for testable expiration-sensitive code.
- Keep header name constants centralized via `ApiHeaderNames`.

## Troubleshooting

- Swagger type inference confusion on list APIs: wrap list in explicit page model (`CursorPage`, `OffsetPage`).
- Time-sensitive tests flaky: inject `TimeProvider` with fixed clock in tests.
- Inconsistent error responses: ensure one global exception mapping layer handles `HttpStatusException` and that callers branch on stable `code`, not message text.
