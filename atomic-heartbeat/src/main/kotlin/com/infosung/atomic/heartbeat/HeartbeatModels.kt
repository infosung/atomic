package com.infosung.atomic.heartbeat

import java.time.Duration

/** Supported deduplication modes for outgoing heartbeat pings. */
enum class DedupMode {
  NONE,
  LEADER,
  PER_INSTANCE,
}

/** Outbound heartbeat signal type. */
enum class HeartbeatEventType {
  START,
  SUCCESS,
  FAIL,
}

/** Outbound heartbeat signal payload. */
data class HeartbeatEvent(
    val type: HeartbeatEventType,
    val message: String? = null,
)

/** Snapshot returned by [DependencyChecker]. */
data class DependencyCheckResult(
    val healthy: Boolean,
    val message: String? = null,
)

/** Configuration for one dependency check loop. */
data class DependencyCheckPlan(
    val id: String,
    val checker: DependencyChecker,
    val required: Boolean = true,
    val interval: Duration,
    val timeout: Duration,
    val staleAfter: Duration = interval.multipliedBy(2),
)
