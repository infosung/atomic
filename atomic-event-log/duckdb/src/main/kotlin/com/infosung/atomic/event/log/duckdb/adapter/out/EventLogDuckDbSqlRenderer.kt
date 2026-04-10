package com.infosung.atomic.event.log.duckdb.adapter.out

import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergTableId

/** Renders service/platform-scoped DuckDB SQL for Parquet and Iceberg datasets. */
class EventLogDuckDbSqlRenderer {
  private val log = System.getLogger(EventLogDuckDbSqlRenderer::class.java.name)

  fun renderParquetQuery(
      dataset: EventLogDuckDbParquetDataset,
      filter: EventLogDuckDbFilter = EventLogDuckDbFilter(),
  ): String =
      buildSelectSql(
              fromClause = renderParquetFromClause(dataset = dataset, filter = filter),
              filter = filter,
          )
          .also { sql ->
            log.log(
                System.Logger.Level.DEBUG,
                "Rendered DuckDB parquet query: serviceId={0}, platform={1}, eventName={2}",
                filter.serviceId ?: "",
                filter.platform?.name ?: "",
                filter.eventName ?: "",
            )
          }

  fun renderParquetImport(
      dataset: EventLogDuckDbParquetDataset,
      targetTable: String,
      filter: EventLogDuckDbFilter = EventLogDuckDbFilter(),
  ): String {
    val sql = buildCreateTableAsSql(targetTable, renderParquetFromClause(dataset, filter), filter)
    log.log(
        System.Logger.Level.DEBUG,
        "Rendered DuckDB parquet import: targetTable={0}, serviceId={1}, platform={2}",
        targetTable,
        filter.serviceId ?: "",
        filter.platform?.name ?: "",
    )
    return sql
  }

  fun renderIcebergAttach(catalog: EventLogDuckDbIcebergCatalog): String {
    val endpointClause = catalog.endpoint?.let { "\n  ENDPOINT '${escapeSqlLiteral(it)}'" } ?: ""
    val sql = buildString {
      append("ATTACH '${escapeSqlLiteral(catalog.source)}' AS ${catalog.alias} (\n")
      append("  TYPE iceberg")
      append(endpointClause)
      append("\n);")
    }
    log.log(
        System.Logger.Level.DEBUG,
        "Rendered DuckDB Iceberg attach: alias={0}, endpointPresent={1}",
        catalog.alias,
        catalog.endpoint != null,
    )
    return sql
  }

  fun renderIcebergMetadataQuery(
      dataset: EventLogDuckDbIcebergMetadataDataset,
      filter: EventLogDuckDbFilter = EventLogDuckDbFilter(),
  ): String {
    val sql =
        buildSelectSql(
            fromClause = "FROM iceberg_scan('${escapeSqlLiteral(dataset.metadataLocation)}')",
            filter = filter,
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Rendered DuckDB Iceberg metadata query: metadataLocation={0}",
        dataset.metadataLocation,
    )
    return sql
  }

  fun renderIcebergQuery(
      catalog: EventLogDuckDbIcebergCatalog,
      tableId: EventLogIcebergTableId,
      filter: EventLogDuckDbFilter = EventLogDuckDbFilter(),
  ): String {
    val sql =
        buildSelectSql(
            fromClause = "FROM ${tableId.qualifiedName(catalog.alias)}",
            filter = filter,
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Rendered DuckDB Iceberg query: alias={0}, table={1}",
        catalog.alias,
        tableId.qualifiedName(),
    )
    return sql
  }

  fun renderCreateClientGa4CompatParquetView(
      dataset: EventLogDuckDbParquetDataset,
      filter: EventLogDuckDbClientFilter = EventLogDuckDbClientFilter(),
      viewName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String {
    val sql =
        buildCreateClientGa4CompatViewSql(
            viewName = viewName,
            fromClause = renderParquetFromClause(dataset, filter.toSourceFilter()),
            filter = filter,
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Rendered DuckDB client GA4 parquet view: viewName={0}, serviceId={1}, platform={2}",
        viewName,
        filter.serviceId ?: "",
        filter.platform?.name ?: "",
    )
    return sql
  }

  fun renderCreateClientGa4CompatIcebergMetadataView(
      dataset: EventLogDuckDbIcebergMetadataDataset,
      filter: EventLogDuckDbClientFilter = EventLogDuckDbClientFilter(),
      viewName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String {
    val sql =
        buildCreateClientGa4CompatViewSql(
            viewName = viewName,
            fromClause = "FROM iceberg_scan('${escapeSqlLiteral(dataset.metadataLocation)}')",
            filter = filter,
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Rendered DuckDB client GA4 Iceberg metadata view: viewName={0}, metadataLocation={1}",
        viewName,
        dataset.metadataLocation,
    )
    return sql
  }

  fun renderCreateClientGa4CompatIcebergView(
      catalog: EventLogDuckDbIcebergCatalog,
      tableId: EventLogIcebergTableId,
      filter: EventLogDuckDbClientFilter = EventLogDuckDbClientFilter(),
      viewName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String {
    val sql =
        buildCreateClientGa4CompatViewSql(
            viewName = viewName,
            fromClause = "FROM ${tableId.qualifiedName(catalog.alias)}",
            filter = filter,
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Rendered DuckDB client GA4 Iceberg view: alias={0}, table={1}, viewName={2}",
        catalog.alias,
        tableId.qualifiedName(),
        viewName,
    )
    return sql
  }

  fun renderClientDailyActiveUsersQuery(
      relationName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String =
      buildClientAnalyticsSql(
              relationName = relationName,
              selectClause =
                  "SELECT event_date, COUNT(DISTINCT COALESCE(user_id, user_pseudo_id)) AS active_users",
              whereClause = null,
              groupByClause = "GROUP BY event_date",
              orderByClause = "ORDER BY event_date ASC",
          )
          .also { sql ->
            log.log(
                System.Logger.Level.DEBUG,
                "Rendered DuckDB client active-users query: relationName={0}",
                relationName,
            )
          }

  fun renderClientActiveInstallsQuery(
      relationName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String =
      buildClientAnalyticsSql(
              relationName = relationName,
              selectClause = "SELECT event_date, COUNT(DISTINCT user_pseudo_id) AS active_installs",
              whereClause = null,
              groupByClause = "GROUP BY event_date",
              orderByClause = "ORDER BY event_date ASC",
          )
          .also {
            log.log(
                System.Logger.Level.DEBUG,
                "Rendered DuckDB client active-installs query: relationName={0}",
                relationName,
            )
          }

  fun renderClientSessionCountQuery(
      relationName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String =
      buildClientAnalyticsSql(
              relationName = relationName,
              selectClause =
                  "SELECT event_date, COUNT(DISTINCT CONCAT(user_pseudo_id, '#', session_id)) AS session_count",
              whereClause = null,
              groupByClause = "GROUP BY event_date",
              orderByClause = "ORDER BY event_date ASC",
          )
          .also {
            log.log(
                System.Logger.Level.DEBUG,
                "Rendered DuckDB client session-count query: relationName={0}",
                relationName,
            )
          }

  fun renderClientScreenViewCountQuery(
      relationName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String =
      buildClientAnalyticsSql(
              relationName = relationName,
              selectClause =
                  "SELECT COALESCE(screen_name, '(unknown)') AS screen_name, COUNT(*) AS screen_view_count",
              whereClause = "WHERE event_name = 'screen_view'",
              groupByClause = "GROUP BY screen_name",
              orderByClause = "ORDER BY screen_view_count DESC, screen_name ASC",
          )
          .also {
            log.log(
                System.Logger.Level.DEBUG,
                "Rendered DuckDB client screen-view query: relationName={0}",
                relationName,
            )
          }

  fun renderClientReleaseAdoptionQuery(
      relationName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String =
      buildClientAnalyticsSql(
              relationName = relationName,
              selectClause =
                  "SELECT app_version, release_channel, COUNT(DISTINCT user_pseudo_id) AS active_installs",
              whereClause = null,
              groupByClause = "GROUP BY app_version, release_channel",
              orderByClause = "ORDER BY active_installs DESC, app_version ASC, release_channel ASC",
          )
          .also {
            log.log(
                System.Logger.Level.DEBUG,
                "Rendered DuckDB client release-adoption query: relationName={0}",
                relationName,
            )
          }

  fun renderClientErrorCountQuery(
      relationName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String =
      buildClientAnalyticsSql(
              relationName = relationName,
              selectClause = "SELECT event_date, COUNT(*) AS error_count",
              whereClause = "WHERE event_type = 'ERROR' OR event_name = 'app_exception'",
              groupByClause = "GROUP BY event_date",
              orderByClause = "ORDER BY event_date ASC",
          )
          .also {
            log.log(
                System.Logger.Level.DEBUG,
                "Rendered DuckDB client error-count query: relationName={0}",
                relationName,
            )
          }

  fun renderClientEngagementTimeQuery(
      relationName: String = DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME,
  ): String =
      buildClientAnalyticsSql(
              relationName = relationName,
              selectClause = "SELECT event_date, SUM(engagement_time_msec) AS engagement_time_msec",
              whereClause = null,
              groupByClause = "GROUP BY event_date",
              orderByClause = "ORDER BY event_date ASC",
          )
          .also {
            log.log(
                System.Logger.Level.DEBUG,
                "Rendered DuckDB client engagement-time query: relationName={0}",
                relationName,
            )
          }

  fun renderEventCountQuery(
      catalog: EventLogDuckDbIcebergCatalog,
      tableId: EventLogIcebergTableId,
      filter: EventLogDuckDbFilter = EventLogDuckDbFilter(),
  ): String {
    val sql = buildString {
      appendLine("SELECT event_name, COUNT(*) AS event_count")
      appendLine("FROM ${tableId.qualifiedName(catalog.alias)}")
      renderWhereClause(filter)?.let { appendLine(it) }
      appendLine("GROUP BY event_name")
      append("ORDER BY event_count DESC;")
    }
    log.log(
        System.Logger.Level.DEBUG,
        "Rendered DuckDB event-count query: alias={0}, table={1}",
        catalog.alias,
        tableId.qualifiedName(),
    )
    return sql
  }

  private fun renderParquetFromClause(
      dataset: EventLogDuckDbParquetDataset,
      filter: EventLogDuckDbFilter,
  ): String {
    val root = dataset.rootUri.trimEnd('/')
    val servicePathToken = filter.serviceId ?: "*"
    val platformPathToken = filter.platform?.name?.lowercase() ?: "*"
    val parquetPath =
        "$root/service_id=$servicePathToken/platform=$platformPathToken/dt=*/hour=*/server_id=*/boot_id=*/*.parquet"
    return "FROM read_parquet('$parquetPath', hive_partitioning = true)"
  }

  private fun buildCreateClientGa4CompatViewSql(
      viewName: String,
      fromClause: String,
      filter: EventLogDuckDbClientFilter,
  ): String = buildString {
    appendLine("CREATE OR REPLACE VIEW ${renderIdentifier(viewName)} AS")
    appendLine("SELECT")
    appendLine("  service_id,")
    appendLine("  platform,")
    appendLine("  event_name,")
    appendLine("  event_type,")
    appendLine("  occurred_at,")
    appendLine("  received_at,")
    appendLine("  CAST(strftime(occurred_at, '%Y%m%d') AS VARCHAR) AS event_date,")
    appendLine("  epoch_us(occurred_at) AS event_timestamp_micros,")
    appendLine("  actor_id AS user_id,")
    appendLine("  userPseudoId AS user_pseudo_id,")
    appendLine("  sessionId AS session_id,")
    appendLine("  COALESCE(engagementTimeMsec, 0) AS engagement_time_msec,")
    appendLine("  appId AS app_id,")
    appendLine("  appVersion AS app_version,")
    appendLine("  screenName AS screen_name,")
    appendLine("  releaseChannel AS release_channel,")
    appendLine("  buildNumber AS build_number,")
    appendLine("  locale,")
    appendLine("  timezone,")
    appendLine("  deviceCategory AS device_category,")
    appendLine("  deviceLanguage AS device_language,")
    appendLine("  operatingSystem AS operating_system,")
    appendLine("  operatingSystemVersion AS operating_system_version,")
    appendLine("  deviceModel AS device_model,")
    appendLine("  deviceBrand AS device_brand,")
    appendLine("  browser,")
    appendLine("  browserVersion AS browser_version,")
    appendLine("  screenResolution AS screen_resolution,")
    appendLine("  trace_id,")
    appendLine("  tags")
    appendLine(fromClause)
    appendLine(renderClientWhereClause(filter))
    append(';')
  }

  private fun buildSelectSql(
      fromClause: String,
      filter: EventLogDuckDbFilter,
  ): String = buildString {
    appendLine("SELECT *")
    appendLine(fromClause)
    renderWhereClause(filter)?.let { appendLine(it) }
    append(';')
  }

  private fun buildCreateTableAsSql(
      targetTable: String,
      fromClause: String,
      filter: EventLogDuckDbFilter,
  ): String = buildString {
    appendLine("CREATE OR REPLACE TABLE ${renderIdentifier(targetTable)} AS")
    appendLine("SELECT *")
    appendLine(fromClause)
    renderWhereClause(filter)?.let { appendLine(it) }
    append(';')
  }

  private fun buildClientAnalyticsSql(
      relationName: String,
      selectClause: String,
      whereClause: String?,
      groupByClause: String,
      orderByClause: String,
  ): String = buildString {
    appendLine(selectClause)
    appendLine("FROM ${renderIdentifier(relationName)}")
    whereClause?.let { appendLine(it) }
    appendLine(groupByClause)
    append("$orderByClause;")
  }

  private fun renderWhereClause(filter: EventLogDuckDbFilter): String? {
    val predicates = buildList {
      filter.serviceId?.let { add("service_id = '${escapeSqlLiteral(it)}'") }
      filter.platform?.let { add("platform = '${escapeSqlLiteral(it.name.lowercase())}'") }
      filter.eventName?.let { add("event_name = '${escapeSqlLiteral(it)}'") }
      filter.dateFrom?.let { add("dt >= DATE '${escapeSqlLiteral(it)}'") }
      filter.dateTo?.let { add("dt < DATE '${escapeSqlLiteral(it)}'") }
    }
    return predicates
        .takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = "WHERE ", separator = " AND ")
  }

  private fun renderClientWhereClause(filter: EventLogDuckDbClientFilter): String {
    val predicates = buildList {
      add(
          filter.platform?.let { "platform = '${escapeSqlLiteral(it.name.lowercase())}'" }
              ?: "platform IN (${CLIENT_PLATFORM_LITERALS.joinToString(", ") { "'$it'" }})")
      filter.serviceId?.let { add("service_id = '${escapeSqlLiteral(it)}'") }
      filter.appId?.let { add("appId = '${escapeSqlLiteral(it)}'") }
      filter.appVersion?.let { add("appVersion = '${escapeSqlLiteral(it)}'") }
      filter.releaseChannel?.let { add("releaseChannel = '${escapeSqlLiteral(it)}'") }
      filter.screenName?.let { add("screenName = '${escapeSqlLiteral(it)}'") }
      filter.locale?.let { add("locale = '${escapeSqlLiteral(it)}'") }
      filter.timezone?.let { add("timezone = '${escapeSqlLiteral(it)}'") }
      filter.eventName?.let { add("event_name = '${escapeSqlLiteral(it)}'") }
      filter.dateFrom?.let { add("dt >= DATE '${escapeSqlLiteral(it)}'") }
      filter.dateTo?.let { add("dt < DATE '${escapeSqlLiteral(it)}'") }
    }
    return predicates.joinToString(prefix = "WHERE ", separator = " AND ")
  }

  private fun EventLogDuckDbClientFilter.toSourceFilter(): EventLogDuckDbFilter =
      EventLogDuckDbFilter(
          serviceId = serviceId,
          platform = platform,
          eventName = eventName,
          dateFrom = dateFrom,
          dateTo = dateTo,
      )

  private fun renderIdentifier(value: String): String {
    require(IDENTIFIER_REGEX.matches(value)) {
      "DuckDB identifier must contain only letters, digits, underscores, or dots."
    }
    return value
  }

  private fun escapeSqlLiteral(value: String): String = value.replace("'", "''")

  private companion object {
    const val DEFAULT_CLIENT_GA4_COMPAT_VIEW_NAME = "client_ga4_compat_events_v1"
    val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_.]*")
    val CLIENT_PLATFORM_LITERALS =
        listOf(
            EventLogPlatform.CLIENT_WEB.name.lowercase(),
            EventLogPlatform.CLIENT_MOBILE.name.lowercase(),
            EventLogPlatform.CLIENT_TABLET.name.lowercase(),
            EventLogPlatform.CLIENT_IPAD.name.lowercase(),
            EventLogPlatform.CLIENT_DESKTOP.name.lowercase(),
        )
  }
}
