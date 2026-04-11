package com.infosung.atomic.event.log.ingest.api.application.model

import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import java.io.Serializable
import java.time.Instant

data class EventLogAsyncIngestCommand(
    val receiptId: String,
    val batch: EventLogIngestIntakeBatch,
    val context: EventLogIngestContext,
    val enqueuedAt: Instant,
) : Serializable

data class EventLogAsyncIngestQueueEntry(
    val sequence: Long,
    val laneId: String,
    val command: EventLogAsyncIngestCommand,
) : Serializable

data class EventLogAsyncIngestEnqueueReceipt(
    val receiptId: String,
    val laneId: String,
    val sequence: Long,
    val queuedAt: Instant,
) : Serializable

data class EventLogAsyncIngestQueueCheckpoint(
    val laneId: String,
    val processedSequence: Long = 0,
    val queuedRequestCount: Int = 0,
    val queuedEstimatedBytes: Long = 0,
) : Serializable

data class EventLogAsyncIngestDrainResult(
    val laneId: String,
    val processed: Int,
    val validationRejected: Int,
    val failed: Int,
    val lastCommittedSequence: Long,
) : Serializable
