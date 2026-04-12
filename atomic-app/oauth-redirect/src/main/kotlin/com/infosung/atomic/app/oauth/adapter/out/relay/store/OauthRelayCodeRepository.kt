package com.infosung.atomic.app.oauth.adapter.out.relay.store

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OauthRelayCodeRepository : JpaRepository<OauthRelayCodeEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT relay
      FROM atomic_oauth_relay_code relay
      WHERE relay.relayCode = :relayCode
      """,
  )
  fun findLockedByRelayCode(
      @Param("relayCode") relayCode: String,
  ): OauthRelayCodeEntity?
}
