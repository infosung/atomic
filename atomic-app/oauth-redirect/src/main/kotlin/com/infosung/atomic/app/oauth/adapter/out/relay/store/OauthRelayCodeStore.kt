package com.infosung.atomic.app.oauth.adapter.out.relay.store

import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import java.time.Instant

/** Store abstraction for one-time OAuth relay payloads. */
interface OauthRelayCodeStore {
  /** Saves relay payload until [expiresAt]. Existing relayCode is overwritten. */
  fun save(
      relayCode: String,
      payload: OauthRelayPayload,
      expiresAt: Instant,
  )

  /**
   * Pops (reads + deletes) relay payload.
   *
   * Returns null when relayCode is unknown, expired, or already consumed.
   */
  fun pop(
      relayCode: String,
      now: Instant,
  ): OauthRelayPayload?
}
