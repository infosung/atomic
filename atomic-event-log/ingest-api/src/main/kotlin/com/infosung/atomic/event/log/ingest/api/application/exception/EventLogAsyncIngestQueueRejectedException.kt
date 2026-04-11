package com.infosung.atomic.event.log.ingest.api.application.exception

/** Raised when the async intake queue cannot accept more requests within the configured budget. */
class EventLogAsyncIngestQueueRejectedException(
    override val message: String,
) : RuntimeException(message)
