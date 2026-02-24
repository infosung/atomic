# atomic.contract Guide

## Summary

`atomic.contract` provides shared DTO, response models, exceptions, and utility classes.

## Main Packages

- `com.infosung.atomic.contract.response`
- `com.infosung.atomic.contract.exception`
- `com.infosung.atomic.contract.header`
- `com.infosung.atomic.contract.time`
- `com.infosung.atomic.contract.random`
- `com.infosung.atomic.contract.security`

## Typical Usage

### BaseResponse

```kotlin
import com.infosung.atomic.contract.response.BaseResponse

val success = BaseResponse(data = mapOf("id" to 1))
val fail = BaseResponse.error<Any>(message = "Invalid request")
```

### CursorPage / OffsetPage

```kotlin
import com.infosung.atomic.contract.response.CursorPage
import com.infosung.atomic.contract.response.OffsetPage

val cursorPage = CursorPage(items = listOf("a", "b"), nextCursor = "cursor-2")
val offsetPage = OffsetPage(items = listOf("a", "b"), page = 1, size = 20, total = 100)
```

### TimeProvider

Use when you need a controllable clock/timezone source.

```kotlin
import com.infosung.atomic.contract.time.TimeProvider
import java.time.Clock
import java.util.TimeZone

val timeProvider = TimeProvider()
val now = timeProvider.nowMillis()

timeProvider.configureClock(Clock.systemUTC())
timeProvider.configureTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
timeProvider.reset()
```

### TraceIdGenerator

```kotlin
import com.infosung.atomic.contract.header.TraceIdGenerator

val traceIdGenerator = TraceIdGenerator()
val traceId = traceIdGenerator.generate()
```

## Bean Registration

`atomic.contract` itself does not require Spring bean registration by default.

If needed, register classes like `TimeProvider` or `TraceIdGenerator` as beans in your app and inject them where required.
