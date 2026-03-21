package com.infosung.atomic.app.oauth.adapter.out.relay.store

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
    jdbcOperations: JdbcOperations,
    transactionTemplate: TransactionTemplate,
    objectMapper: ObjectMapper,
    timeProvider: TimeProvider = TimeProvider(),
    tableName: String,
) :
    EntityOauthRelayCodeStoreAdapter(
        jdbcOperations = jdbcOperations,
        transactionTemplate = transactionTemplate,
        objectMapper = objectMapper,
        timeProvider = timeProvider,
        tableName = tableName,
    )
