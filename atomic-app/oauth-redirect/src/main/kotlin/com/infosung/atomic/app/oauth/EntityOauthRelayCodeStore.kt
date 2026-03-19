package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.relay.store.EntityOauthRelayCodeStoreAdapter
import com.infosung.atomic.contract.time.TimeProvider
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/**
 * RDB table-backed relay store implementation.
 *
 * Expiration is checked on consume (`pop`). Run a periodic cleanup job to remove expired rows that
 * were never consumed.
 */
class EntityOauthRelayCodeStore(
    private val jdbcOperations: JdbcOperations,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
    private val timeProvider: TimeProvider = TimeProvider(),
    tableName: String,
) :
    EntityOauthRelayCodeStoreAdapter(
        jdbcOperations = jdbcOperations,
        transactionTemplate = transactionTemplate,
        objectMapper = objectMapper,
        timeProvider = timeProvider,
        tableName = tableName,
    )
