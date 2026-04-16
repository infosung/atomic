package com.infosung.atomic.contract.bootstrap

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BootstrapReadinessContractTest {
  @Test
  fun `ready bootstrap should build canonical ready contract`() {
    val response =
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
            bootstrap =
                BootstrapReadiness.ready(
                    latestSnapshotExists = true,
                    serverHeadSeq = 1234,
                    snapshotSeq = 1200,
                    deltaRetentionFloorSeq = 900,
                ),
            capabilities =
                BootstrapCapabilitySet(
                    values =
                        linkedMapOf(
                            "pushHintEnabled" to true,
                            "websocketHintEnabled" to true,
                        ),
                ),
            server = BootstrapReadinessServer(serverTime = Instant.parse("2026-04-15T12:34:56Z")),
        )

    assertEquals(1, response.responseVersion)
    assertEquals(BootstrapReadinessState.READY, response.bootstrap.bootstrapState)
    assertFalse(response.bootstrap.resetRequired)
    assertEquals(BootstrapReadinessReasonCode.NONE, response.bootstrap.reasonCode)
    assertEquals(BootstrapReadinessNextAction.PROCEED, response.bootstrap.nextAction)
    assertTrue(response.capabilities.values.getValue("pushHintEnabled"))
  }

  @Test
  fun `reset required bootstrap should preserve reason and action`() {
    val bootstrap =
        BootstrapReadiness.resetRequired(
            reasonCode = BootstrapReadinessReasonCode.CLIENT_SCHEMA_TOO_OLD,
            nextAction = BootstrapReadinessNextAction.UPGRADE_CLIENT,
            latestSnapshotExists = false,
        )

    assertEquals(BootstrapReadinessState.RESET_REQUIRED, bootstrap.bootstrapState)
    assertTrue(bootstrap.resetRequired)
    assertEquals(BootstrapReadinessReasonCode.CLIENT_SCHEMA_TOO_OLD, bootstrap.reasonCode)
    assertEquals(BootstrapReadinessNextAction.UPGRADE_CLIENT, bootstrap.nextAction)
  }

  @Test
  fun `bootstrap readiness should reject inconsistent reset contract`() {
    assertFailsWith<IllegalArgumentException> {
      BootstrapReadiness(
          bootstrapState = BootstrapReadinessState.READY,
          resetRequired = true,
          reasonCode = BootstrapReadinessReasonCode.NONE,
          nextAction = BootstrapReadinessNextAction.PROCEED,
      )
    }
  }

  @Test
  fun `capability set should reject blank names`() {
    assertFailsWith<IllegalArgumentException> { BootstrapCapabilitySet(values = mapOf("" to true)) }
  }
}
