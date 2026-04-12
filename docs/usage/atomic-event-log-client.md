# atomic.event.log Client Guide

This guide defines the shared client-side event envelope for `atomic.event.log`.

The goal is not to force every product to share the same business schema.
The goal is to keep the outer transport and reserved client fields stable enough that:

- Windows/Desktop clients can send logs even when GA is unavailable
- other clients can stay close to GA-style event naming and dimensions
- DuckDB canned queries can work across services with one common view

## 1. What Must Be Common

Only the outer envelope and reserved client fields are common.

### Common batch envelope

Every client batch uses:

- `schemaVersion`
- `serviceId`
- `events`

Every event uses:

- `eventId`
- `eventName`
- `occurredAt`
- `platform`
- `platformPayload`
- optional `eventType`
- optional `actorId`
- optional `traceId`
- optional `tags`
- optional `businessPayload`

### Client platform values

Use one of:

- `CLIENT_WEB`
- `CLIENT_MOBILE`
- `CLIENT_TABLET`
- `CLIENT_IPAD`
- `CLIENT_DESKTOP`

If the event is not a client application event, do not force it into the client schema.
API, WebSocket, and server logs have their own reserved payload types.

## 2. Reserved Client Fields

Client events map into `ClientEventLogPayload`.

### Required fields

| Field | Meaning |
|---|---|
| `appId` | Stable application identifier |
| `appVersion` | Release version visible to product/support teams |
| `userPseudoId` | Stable pseudonymous install/device/user key for analytics |
| `sessionId` | Numeric session identifier greater than zero |

### Optional fields

| Field | Meaning |
|---|---|
| `engagementTimeMsec` | Per-event engagement duration |
| `screenName` | Screen/page name |
| `releaseChannel` | `prod`, `beta`, `internal` style channel |
| `buildNumber` | CI/build number |
| `locale` | App locale |
| `timezone` | Client timezone |
| `deviceCategory` | Product-defined device family |
| `deviceLanguage` | Device preferred language |
| `operatingSystem` | OS name |
| `operatingSystemVersion` | OS version |
| `deviceModel` | Device model |
| `deviceBrand` | Device vendor/brand |
| `browser` | Browser name for web |
| `browserVersion` | Browser version for web |
| `screenResolution` | Screen/window resolution |

Validation rules from the current implementation:

- `appId`, `appVersion`, and `userPseudoId` must not be blank
- `sessionId` must be greater than zero
- `engagementTimeMsec`, when provided, must be zero or greater

## 3. What Should Stay GA-Aligned

The collector is not GA itself, but it is most useful when client naming stays close to GA4
conventions.

Recommended event names:

- `session_start`
- `screen_view`
- `user_engagement`
- `app_exception`
- product-specific custom events such as `signup_completed`, `totp_generated`, `vault_opened`

Recommended event type mapping:

- lifecycle-like events -> `LIFECYCLE`
- button/action events -> `ACTION`
- error/crash events -> `ERROR`

DuckDB canned queries already assume these conventions:

- screen view query expects `event_name = 'screen_view'`
- error query counts `event_type = 'ERROR'` or `event_name = 'app_exception'`

If your services choose very different naming, the raw collector still works, but the canned
analytics queries become less useful.

## 4. `businessPayload` Rules

`businessPayload` is intentionally opaque to the collector server.

Allowed values:

- string
- integer
- decimal
- boolean

Not allowed:

- nested objects
- arrays

This keeps the storage and query layers predictable and avoids the collector needing to know each
service's business schema.

## 5. Recommended Identity Policy

Use two different identity concepts:

- `userPseudoId`
  - analytics-safe primary identity
  - should exist for anonymous and signed-in users
- `actorId`
  - optional stronger identity when your product already has a verified account/session context

Recommended policy:
- always send `userPseudoId`
- send `actorId` only when your client/runtime already has a legitimate authenticated principal
- never treat `actorId` as a replacement for `userPseudoId`

In the GA-compatible DuckDB view:
- `userPseudoId` becomes `user_pseudo_id`
- `actorId` becomes `user_id`

## 6. Canonical Client Event Example

```json
{
  "schemaVersion": 1,
  "serviceId": "fillingheart",
  "events": [
    {
      "eventId": "4b3f3cf4-bf5d-4a93-9ab7-8d32bcf4ef90",
      "eventName": "screen_view",
      "eventType": "LIFECYCLE",
      "occurredAt": "2026-04-12T08:00:00Z",
      "platform": "CLIENT_DESKTOP",
      "tags": ["ga-aligned", "windows"],
      "platformPayload": {
        "appId": "com.infosung.fillingheart.windows",
        "appVersion": "1.4.2",
        "userPseudoId": "win-b2d7d1c87d7a",
        "sessionId": 1712908800,
        "engagementTimeMsec": 1250,
        "screenName": "home",
        "releaseChannel": "prod",
        "buildNumber": "4102",
        "locale": "ko-KR",
        "timezone": "Asia/Seoul",
        "deviceCategory": "desktop",
        "deviceLanguage": "ko",
        "operatingSystem": "Windows",
        "operatingSystemVersion": "11",
        "deviceModel": "PC",
        "deviceBrand": "generic",
        "screenResolution": "1920x1080"
      },
      "businessPayload": {
        "experiment": "home_hero_a",
        "isReturningUser": true
      }
    }
  ]
}
```

## 7. Windows / Flutter Guidance

For the Windows fallback scenario, keep the client shape exactly the same as other platforms.

Recommended approach:

1. Keep your internal analytics event names aligned with GA4 where possible.
2. Reuse the same domain event producers already used by GA-enabled platforms.
3. Send those events to the collector only on platforms where GA is unavailable or intentionally
   disabled.
4. Preserve `serviceId`, `eventName`, and reserved client fields so downstream DuckDB queries stay
   comparable.

The collector is most useful when Windows is a transport replacement, not a schema fork.

## 8. Minimal Send Example

Any HTTP client can send the batch, but a tiny emitter example removes the last-mile ambiguity.

```dart
Future<void> sendAtomicEventLog({
  required Uri collectorUri,
  required Map<String, Object?> eventBatch,
  required String collectorToken,
}) async {
  final response = await http.post(
    collectorUri,
    headers: {
      'content-type': 'application/json',
      'authorization': 'Bearer $collectorToken',
    },
    body: jsonEncode(eventBatch),
  );

  if (response.statusCode == 202) {
    return;
  }
  if (response.statusCode == 503) {
    throw Exception('collector queue is saturated; retry with backoff');
  }
  if (response.statusCode >= 400) {
    throw Exception('collector rejected the payload: ${response.body}');
  }
}
```

Typical collector target:

- `POST /api/v1/event-logs:batch`

Minimal response handling:

- `202` -> accepted into async intake queue
- `503` -> queue overflow, retry later
- `400` -> payload or schema problem, fix client payload before retry

## 9. Batching and Retry Policy

The API accepts both single-event and multi-event batches.
In practice, most clients will send one event per request, and that is fine.

Recommended client behavior:

- batch opportunistically, but do not require batching to function
- generate a unique `eventId` per event before enqueue
- retry only on transport failure or `503`
- do not retry `400` validation failures blindly
- keep retry backoff finite to avoid duplicate storms after reconnect

Response interpretation:

- `202 Accepted`
  - the server accepted the batch into the async intake queue
  - export is not complete yet
- `200 OK`
  - only for `SYNC` collector mode
  - full ingest result is already known
- `400`
  - malformed request or invalid payload
- `503`
  - queue overflow; back off and retry later

## 10. Session Guidance

The shared client model uses a numeric `sessionId`.

Recommended policy:

- create one session id per app launch or foreground session boundary
- keep it stable across all events in the same session
- rotate on clear session boundaries

If you already have GA-style session logic elsewhere, mirror the same policy here.

## 11. Multi-Service Guidance

Use `serviceId` to separate products that share the same collector.

Examples:

- `fillingheart`
- `totp`
- `launcher`

Do not encode product identity into event names when `serviceId` already distinguishes them.
Keep event names reusable and let `serviceId` carry the top-level namespace.

## 12. Suggested Client Checklist

- stable `serviceId`
- stable `appId`
- non-blank `appVersion`
- generated `eventId` per event
- pseudonymous `userPseudoId`
- positive `sessionId`
- GA-aligned event names where practical
- scalar-only `businessPayload`
- no secrets or raw credentials inside payloads
