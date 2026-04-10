package com.infosung.atomic.event.log.parquet.application.service

import com.infosung.atomic.event.log.parquet.adapter.out.publication.ParquetOnlyEventLogPublicationStrategy
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportContext
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportPolicy
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportResult
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetPartition
import com.infosung.atomic.event.log.parquet.application.model.estimatedByteSize
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogParquetFilePlan
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogParquetFileRepository
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationRequest
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationStrategy
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublishedParquetFile
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpool
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolEntry
import kotlin.math.max

/**
 * Groups spool entries into Parquet files and publishes them through one mode-specific strategy.
 */
class EventLogParquetExportCoordinator(
    private val spool: EventLogSpool,
    private val partitioner: EventLogParquetPartitioner,
    private val keyFactory: EventLogParquetObjectKeyFactory,
    private val repository: EventLogParquetFileRepository,
    private val publicationStrategy: EventLogPublicationStrategy =
        ParquetOnlyEventLogPublicationStrategy(),
    private val exportPolicy: EventLogParquetExportPolicy = EventLogParquetExportPolicy(),
    private val nanoTimeSource: () -> Long = System::nanoTime,
) {
  private val log = System.getLogger(EventLogParquetExportCoordinator::class.java.name)

  fun export(context: EventLogParquetExportContext): EventLogParquetExportResult {
    val pending = spool.pending(limit = exportPolicy.maxPendingDrain)
    if (pending.isEmpty()) {
      log.log(
          System.Logger.Level.DEBUG,
          "Parquet export skipped because spool is empty: committedSequence={0}",
          spool.checkpoint().committedSequence,
      )
      return EventLogParquetExportResult(
          exportedFileCount = 0,
          exportedRecordCount = 0,
          committedThroughSequence = spool.checkpoint().committedSequence,
          publicationMode = publicationStrategy.mode,
      )
    }

    val publishedFiles = mutableListOf<EventLogPublishedParquetFile>()
    var committedThrough = spool.checkpoint().committedSequence
    val exportStartedAt = nanoTimeSource()
    for (plan in planSequence(pending, context)) {
      if (publishedFiles.isNotEmpty() && isFlushBudgetExceeded(exportStartedAt)) {
        log.log(
            System.Logger.Level.DEBUG,
            "Parquet export stopped by flush-duration budget: publishedFiles={0}, committedThrough={1}",
            publishedFiles.size,
            committedThrough,
        )
        break
      }
      val records = plan.entries.map(EventLogSpoolEntry::record)
      val staged = repository.stage(plan = plan, records = records)
      publishedFiles += repository.promote(staged)
      committedThrough = max(committedThrough, plan.entries.maxOf(EventLogSpoolEntry::sequence))
    }
    if (publishedFiles.isEmpty()) {
      log.log(
          System.Logger.Level.DEBUG,
          "Parquet export skipped after planning because no file was published: pending={0}",
          pending.size,
      )
      return EventLogParquetExportResult(
          exportedFileCount = 0,
          exportedRecordCount = 0,
          committedThroughSequence = spool.checkpoint().committedSequence,
          publicationMode = publicationStrategy.mode,
      )
    }
    val publicationReceipt =
        publicationStrategy.publish(
            EventLogPublicationRequest(
                files = publishedFiles,
                exportContext = context,
            ))
    require(publicationReceipt.mode == publicationStrategy.mode) {
      "publication strategy returned mismatched mode. expected=${publicationStrategy.mode}, actual=${publicationReceipt.mode}"
    }

    spool.markCommittedThrough(committedThrough)
    val result =
        EventLogParquetExportResult(
            exportedFileCount = publishedFiles.size,
            exportedRecordCount = publishedFiles.sumOf { it.recordCount.toInt() },
            committedThroughSequence = committedThrough,
            publicationMode = publicationReceipt.mode,
            publicationMetadata = publicationReceipt.metadata,
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Parquet export finished: files={0}, records={1}, committedThrough={2}, publicationMode={3}",
        result.exportedFileCount,
        result.exportedRecordCount,
        result.committedThroughSequence,
        result.publicationMode.name,
    )
    return result
  }

  private fun planSequence(
      pending: List<EventLogSpoolEntry>,
      context: EventLogParquetExportContext,
  ): Sequence<EventLogParquetFilePlan> = sequence {
    val states = linkedMapOf<EventLogParquetPartition, PartitionPlanState>()
    pending.forEach { entry ->
      val partition = partitioner.partition(entry.record)
      val state =
          states.getOrPut(partition) {
            PartitionPlanState(
                partition = partition,
                nextFileIndex = 0,
                currentEntries = mutableListOf(),
                currentEstimatedBytes = 0L,
            )
          }
      val entryBytes = entry.record.estimatedByteSize()
      if (state.shouldRotate(entryBytes, exportPolicy)) {
        yield(state.toPlan(context))
        state.rotate()
      }
      state.currentEntries += entry
      state.currentEstimatedBytes += entryBytes
    }
    states.values.forEach { state ->
      if (state.currentEntries.isNotEmpty()) {
        yield(state.toPlan(context))
      }
    }
  }

  private fun isFlushBudgetExceeded(exportStartedAt: Long): Boolean {
    val maxFlushDuration = exportPolicy.maxFlushDuration ?: return false
    return nanoTimeSource() - exportStartedAt >= maxFlushDuration.toNanos()
  }

  private inner class PartitionPlanState(
      val partition: EventLogParquetPartition,
      var nextFileIndex: Int,
      var currentEntries: MutableList<EventLogSpoolEntry>,
      var currentEstimatedBytes: Long,
  ) {
    fun shouldRotate(
        nextEntryBytes: Long,
        policy: EventLogParquetExportPolicy,
    ): Boolean =
        currentEntries.isNotEmpty() &&
            (currentEntries.size >= policy.maxRecordsPerFile ||
                currentEstimatedBytes + nextEntryBytes > policy.maxEstimatedBytesPerFile)

    fun toPlan(context: EventLogParquetExportContext): EventLogParquetFilePlan =
        EventLogParquetFilePlan(
            partition = partition,
            objectKeys =
                keyFactory.create(
                    partition = partition,
                    context = context,
                    fileIndex = nextFileIndex,
                ),
            entries = currentEntries.toList(),
        )

    fun rotate() {
      nextFileIndex += 1
      currentEntries = mutableListOf()
      currentEstimatedBytes = 0L
    }
  }
}
