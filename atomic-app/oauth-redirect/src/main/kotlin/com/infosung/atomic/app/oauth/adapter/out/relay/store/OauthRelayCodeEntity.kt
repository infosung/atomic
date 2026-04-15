package com.infosung.atomic.app.oauth.adapter.out.relay.store

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.time.LocalDateTime
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable

@Entity(name = "atomic_oauth_relay_code")
@Table(
    name = OauthRelayCodeTableNamePolicy.DEFAULT_TABLE_NAME,
    indexes =
        [
            Index(
                name = "idx_atomic_oauth_relay_code_expires_at",
                columnList = "expires_at",
            ),
        ],
)
class OauthRelayCodeEntity(
    @Id @Column(name = "relay_code", length = 255) val relayCode: String = "",
    @Column(name = "payload_json") @JdbcTypeCode(SqlTypes.LONGVARCHAR) var payloadJson: String = "",
    @Column(name = "expires_at") var expiresAt: LocalDateTime = LocalDateTime.MIN,
    @Column(name = "created_at") var createdAt: LocalDateTime = LocalDateTime.MIN,
) : Persistable<String> {
  @Transient private var newEntity: Boolean = true

  override fun getId(): String = relayCode

  override fun isNew(): Boolean = newEntity

  fun overwrite(
      payloadJson: String,
      expiresAt: LocalDateTime,
      createdAt: LocalDateTime,
  ) {
    this.payloadJson = payloadJson
    this.expiresAt = expiresAt
    this.createdAt = createdAt
  }

  @PostPersist
  @PostLoad
  private fun markNotNew() {
    newEntity = false
  }
}
