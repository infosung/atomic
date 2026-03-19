package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStoreAdapter
import com.infosung.atomic.contract.time.TimeProvider

/** In-memory relay store for single-instance/local environments. */
class InMemoryOauthRelayCodeStore(
    private val cleanupInterval: Int = 100,
    private val timeProvider: TimeProvider = TimeProvider(),
) :
    InMemoryOauthRelayCodeStoreAdapter(
        cleanupInterval = cleanupInterval,
        timeProvider = timeProvider,
    )
