package com.infosung.atomic.app.oauth.adapter.out.relay.store

import com.infosung.atomic.contract.time.TimeProvider

/** In-memory relay store for single-instance/local environments. */
class InMemoryOauthRelayCodeStore(
    cleanupInterval: Int = 100,
    timeProvider: TimeProvider = TimeProvider(),
) :
    InMemoryOauthRelayCodeStoreAdapter(
        cleanupInterval = cleanupInterval,
        timeProvider = timeProvider,
    )
