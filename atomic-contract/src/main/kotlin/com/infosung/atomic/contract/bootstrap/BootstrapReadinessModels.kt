package com.infosung.atomic.contract.bootstrap

import java.time.Instant

enum class BootstrapReadinessSessionState {
  ACTIVE,
}

enum class BootstrapReadinessDeviceState {
  ACTIVE,
  STALE,
}

enum class BootstrapReadinessState {
  READY,
  RESET_REQUIRED,
}

enum class BootstrapReadinessReasonCode {
  NONE,
  CURSOR_GAP,
  DELTA_RETENTION_EXPIRED,
  LOCAL_STATE_INCOMPATIBLE,
  CLIENT_SCHEMA_TOO_OLD,
  SNAPSHOT_UNAVAILABLE,
}

enum class BootstrapReadinessNextAction {
  PROCEED,
  RESET_FROM_SERVER_SNAPSHOT,
  UPGRADE_CLIENT,
  RETRY_LATER,
}

data class BootstrapReadinessResponse(
    val responseVersion: Int = 1,
    val account: BootstrapReadinessAccount,
    val session: BootstrapReadinessSession,
    val device: BootstrapReadinessDevice,
    val bootstrap: BootstrapReadiness,
    val capabilities: BootstrapCapabilitySet = BootstrapCapabilitySet(),
    val server: BootstrapReadinessServer,
) {
  init {
    require(responseVersion > 0) { "responseVersion must be greater than zero." }
  }
}

data class BootstrapReadinessAccount(
    val accountId: String,
) {
  init {
    require(accountId.isNotBlank()) { "accountId must not be blank." }
  }
}

data class BootstrapReadinessSession(
    val sessionState: BootstrapReadinessSessionState,
    val accessTokenExpiresAt: Instant,
    val refreshInactivityExpiresAt: Instant,
    val refreshAbsoluteExpiresAt: Instant,
) {
  init {
    require(!refreshInactivityExpiresAt.isBefore(accessTokenExpiresAt)) {
      "refreshInactivityExpiresAt must be greater than or equal to accessTokenExpiresAt."
    }
    require(!refreshAbsoluteExpiresAt.isBefore(refreshInactivityExpiresAt)) {
      "refreshAbsoluteExpiresAt must be greater than or equal to refreshInactivityExpiresAt."
    }
  }
}

data class BootstrapReadinessDevice(
    val deviceId: String,
    val deviceState: BootstrapReadinessDeviceState,
) {
  init {
    require(deviceId.isNotBlank()) { "deviceId must not be blank." }
  }
}

data class BootstrapReadiness(
    val bootstrapState: BootstrapReadinessState,
    val resetRequired: Boolean,
    val reasonCode: BootstrapReadinessReasonCode,
    val nextAction: BootstrapReadinessNextAction,
    val latestSnapshotExists: Boolean? = null,
    val serverHeadSeq: Long? = null,
    val snapshotSeq: Long? = null,
    val deltaRetentionFloorSeq: Long? = null,
) {
  init {
    require(resetRequired == (bootstrapState == BootstrapReadinessState.RESET_REQUIRED)) {
      "resetRequired must match bootstrapState."
    }
    if (!resetRequired) {
      require(reasonCode == BootstrapReadinessReasonCode.NONE) {
        "READY bootstrap must use NONE reasonCode."
      }
      require(nextAction == BootstrapReadinessNextAction.PROCEED) {
        "READY bootstrap must use PROCEED nextAction."
      }
    } else {
      require(reasonCode != BootstrapReadinessReasonCode.NONE) {
        "RESET_REQUIRED bootstrap must provide a concrete reasonCode."
      }
      require(nextAction != BootstrapReadinessNextAction.PROCEED) {
        "RESET_REQUIRED bootstrap must not use PROCEED nextAction."
      }
    }
    require(serverHeadSeq == null || serverHeadSeq >= 0) { "serverHeadSeq must not be negative." }
    require(snapshotSeq == null || snapshotSeq >= 0) { "snapshotSeq must not be negative." }
    require(deltaRetentionFloorSeq == null || deltaRetentionFloorSeq >= 0) {
      "deltaRetentionFloorSeq must not be negative."
    }
  }

  companion object {
    fun ready(
        latestSnapshotExists: Boolean? = null,
        serverHeadSeq: Long? = null,
        snapshotSeq: Long? = null,
        deltaRetentionFloorSeq: Long? = null,
    ): BootstrapReadiness =
        BootstrapReadiness(
            bootstrapState = BootstrapReadinessState.READY,
            resetRequired = false,
            reasonCode = BootstrapReadinessReasonCode.NONE,
            nextAction = BootstrapReadinessNextAction.PROCEED,
            latestSnapshotExists = latestSnapshotExists,
            serverHeadSeq = serverHeadSeq,
            snapshotSeq = snapshotSeq,
            deltaRetentionFloorSeq = deltaRetentionFloorSeq,
        )

    fun resetRequired(
        reasonCode: BootstrapReadinessReasonCode,
        nextAction: BootstrapReadinessNextAction,
        latestSnapshotExists: Boolean? = null,
        serverHeadSeq: Long? = null,
        snapshotSeq: Long? = null,
        deltaRetentionFloorSeq: Long? = null,
    ): BootstrapReadiness =
        BootstrapReadiness(
            bootstrapState = BootstrapReadinessState.RESET_REQUIRED,
            resetRequired = true,
            reasonCode = reasonCode,
            nextAction = nextAction,
            latestSnapshotExists = latestSnapshotExists,
            serverHeadSeq = serverHeadSeq,
            snapshotSeq = snapshotSeq,
            deltaRetentionFloorSeq = deltaRetentionFloorSeq,
        )
  }
}

data class BootstrapCapabilitySet(
    val values: Map<String, Boolean> = emptyMap(),
) {
  init {
    values.keys.forEach { key -> require(key.isNotBlank()) { "capability key must not be blank." } }
  }
}

data class BootstrapReadinessServer(
    val serverTime: Instant,
)
