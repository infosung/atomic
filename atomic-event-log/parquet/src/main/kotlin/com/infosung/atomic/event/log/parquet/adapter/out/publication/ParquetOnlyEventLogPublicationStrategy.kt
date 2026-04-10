package com.infosung.atomic.event.log.parquet.adapter.out.publication

import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationMode
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationReceipt
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationRequest
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationStrategy

/** Default strategy that treats finalized Parquet files as the terminal artifact. */
class ParquetOnlyEventLogPublicationStrategy : EventLogPublicationStrategy {
  private val log = System.getLogger(ParquetOnlyEventLogPublicationStrategy::class.java.name)

  override val mode: EventLogPublicationMode = EventLogPublicationMode.PARQUET_ONLY

  override fun publish(request: EventLogPublicationRequest): EventLogPublicationReceipt {
    val receipt =
        EventLogPublicationReceipt(
            mode = mode,
            publishedFileCount = request.files.size,
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Parquet-only publication finished: files={0}, serverId={1}, flushSequence={2}",
        receipt.publishedFileCount,
        request.exportContext.serverId,
        request.exportContext.flushSequence,
    )
    return receipt
  }
}
