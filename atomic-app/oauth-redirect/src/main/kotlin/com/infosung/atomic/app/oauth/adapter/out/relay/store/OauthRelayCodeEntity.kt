package com.infosung.atomic.app.oauth.adapter.out.relay.store

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

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
    @Column(name = "payload_json") @JdbcTypeCode(SqlTypes.LONGVARCHAR) val payloadJson: String = "",
    @Column(name = "expires_at") val expiresAt: LocalDateTime = LocalDateTime.MIN,
    @Column(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.MIN,
)
