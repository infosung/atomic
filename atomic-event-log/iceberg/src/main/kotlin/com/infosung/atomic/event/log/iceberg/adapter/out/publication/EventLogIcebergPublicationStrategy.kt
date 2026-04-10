package com.infosung.atomic.event.log.iceberg.adapter.out.publication

import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergCommitRequest
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergCommitStatus
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergDataFile
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergTableId
import com.infosung.atomic.event.log.iceberg.application.port.out.EventLogIcebergCatalog
import com.infosung.atomic.event.log.iceberg.application.port.out.EventLogIcebergTableStrategy
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportContext
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationMode
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationReceipt
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationRequest
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationStrategy
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublishedParquetFile

/** Publication strategy for Iceberg tables backed by HadoopCatalog layouts. */
class HadoopCatalogEventLogPublicationStrategy(
    tableStrategy: EventLogIcebergTableStrategy,
    catalog: EventLogIcebergCatalog,
    private val warehouseLocation: String,
    additionalSnapshotProperties: Map<String, String> = emptyMap(),
) :
    BaseIcebergEventLogPublicationStrategy(
        mode = EventLogPublicationMode.ICEBERG_HADOOP,
        tableStrategy = tableStrategy,
        catalog = catalog,
        publicationMetadata = mapOf("warehouseLocation" to warehouseLocation),
        snapshotProperties =
            mapOf("event_log.warehouse_location" to warehouseLocation) +
                additionalSnapshotProperties,
    ) {
  init {
    require(warehouseLocation.isNotBlank()) { "warehouseLocation must not be blank." }
  }
}

/** Publication strategy for Iceberg tables backed by REST catalogs. */
class RestCatalogEventLogPublicationStrategy(
    tableStrategy: EventLogIcebergTableStrategy,
    catalog: EventLogIcebergCatalog,
    private val catalogEndpoint: String,
    additionalSnapshotProperties: Map<String, String> = emptyMap(),
) :
    BaseIcebergEventLogPublicationStrategy(
        mode = EventLogPublicationMode.ICEBERG_REST,
        tableStrategy = tableStrategy,
        catalog = catalog,
        publicationMetadata = mapOf("catalogEndpoint" to catalogEndpoint),
        snapshotProperties =
            mapOf("event_log.catalog_endpoint" to catalogEndpoint) + additionalSnapshotProperties,
    ) {
  init {
    require(catalogEndpoint.isNotBlank()) { "catalogEndpoint must not be blank." }
  }
}

abstract class BaseIcebergEventLogPublicationStrategy(
    override val mode: EventLogPublicationMode,
    private val tableStrategy: EventLogIcebergTableStrategy,
    private val catalog: EventLogIcebergCatalog,
    private val publicationMetadata: Map<String, String>,
    private val snapshotProperties: Map<String, String>,
) : EventLogPublicationStrategy {
  private val log = System.getLogger(javaClass.name)

  override fun publish(request: EventLogPublicationRequest): EventLogPublicationReceipt {
    val groupedFiles = request.files.groupBy { tableStrategy.resolve(it.partition.serviceId) }
    val commitResults =
        groupedFiles.map { (tableId, files) ->
          val commitRequest =
              EventLogIcebergCommitRequest(
                  tableId = tableId,
                  dataFiles = files.map(EventLogPublishedParquetFile::toIcebergDataFile),
                  commitId =
                      createCommitId(
                          mode = mode,
                          context = request.exportContext,
                          tableId = tableId,
                      ),
                  snapshotProperties =
                      snapshotProperties +
                          mapOf(
                              "event_log.publication_mode" to mode.name.lowercase(),
                              "event_log.server_id" to request.exportContext.serverId,
                              "event_log.boot_id" to request.exportContext.bootId,
                              "event_log.flush_sequence" to
                                  request.exportContext.flushSequence.toString(),
                              "event_log.table" to tableId.qualifiedName(),
                          ),
              )
          val commitResult = catalog.commit(commitRequest)
          require(commitResult.commitId == commitRequest.commitId) {
            "Iceberg catalog returned mismatched commitId. expected=${commitRequest.commitId}, actual=${commitResult.commitId}"
          }
          require(commitResult.committedFileCount == commitRequest.dataFiles.size) {
            "Iceberg catalog returned committedFileCount=${commitResult.committedFileCount}, expected=${commitRequest.dataFiles.size}"
          }
          commitResult
        }
    val appliedCommitCount =
        commitResults.count { it.status == EventLogIcebergCommitStatus.APPLIED }
    val replayedCommitCount =
        commitResults.count { it.status == EventLogIcebergCommitStatus.ALREADY_COMMITTED }
    val receipt =
        EventLogPublicationReceipt(
            mode = mode,
            publishedFileCount = request.files.size,
            metadata =
                publicationMetadata +
                    mapOf(
                        "appliedCommitCount" to appliedCommitCount.toString(),
                        "replayedCommitCount" to replayedCommitCount.toString(),
                    ),
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Iceberg publication finished: mode={0}, tables={1}, files={2}, appliedCommits={3}, replayedCommits={4}",
        mode.name,
        groupedFiles.size,
        receipt.publishedFileCount,
        appliedCommitCount,
        replayedCommitCount,
    )
    return receipt
  }
}

private fun EventLogPublishedParquetFile.toIcebergDataFile(): EventLogIcebergDataFile =
    EventLogIcebergDataFile(
        objectKey = objectKey,
        serviceId = partition.serviceId,
        platform = partition.platform,
        partitionValues =
            mapOf(
                "service_id" to partition.serviceId,
                "platform" to partition.platform.name.lowercase(),
                "dt" to partition.dt.toString(),
                "hour" to partition.hour.toString().padStart(2, '0'),
            ),
        recordCount = recordCount,
        occurredAtMin = occurredAtMin,
        occurredAtMax = occurredAtMax,
    )

private fun createCommitId(
    mode: EventLogPublicationMode,
    context: EventLogParquetExportContext,
    tableId: EventLogIcebergTableId,
): String =
    "${mode.name.lowercase()}:${context.serverId}:${context.bootId}:${context.flushSequence}:${tableId.qualifiedName()}"
