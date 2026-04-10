package com.infosung.atomic.event.log.duckdb

import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbClientFilter
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbFilter
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbIcebergCatalog
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbIcebergMetadataDataset
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbParquetDataset
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbSqlRenderer
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergTableId
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EventLogDuckDbSqlRendererTest {
  private val renderer = EventLogDuckDbSqlRenderer()

  @Test
  fun `parquet query renders service and platform filters`() {
    val sql =
        renderer.renderParquetQuery(
            dataset = EventLogDuckDbParquetDataset(rootUri = "s3://bucket/event-log"),
            filter =
                EventLogDuckDbFilter(
                    serviceId = "totp",
                    platform = EventLogPlatform.API,
                    eventName = "api.request",
                    dateFrom = "2026-04-10",
                    dateTo = "2026-04-11",
                ),
        )

    assertTrue(sql.contains("service_id=totp"))
    assertTrue(sql.contains("hour=*/server_id=*/boot_id=*/*.parquet"))
    assertTrue(sql.contains("platform = 'api'"))
    assertTrue(sql.contains("event_name = 'api.request'"))
  }

  @Test
  fun `parquet import query renders target table and filters`() {
    val sql =
        renderer.renderParquetImport(
            dataset = EventLogDuckDbParquetDataset(rootUri = "s3://bucket/event-log"),
            targetTable = "event_log_cache",
            filter =
                EventLogDuckDbFilter(
                    serviceId = "totp",
                    dateFrom = "2026-04-10",
                ),
        )

    assertTrue(sql.contains("CREATE OR REPLACE TABLE event_log_cache AS"))
    assertTrue(sql.contains("read_parquet"))
    assertTrue(sql.contains("service_id = 'totp'"))
  }

  @Test
  fun `iceberg metadata query renders iceberg scan`() {
    val sql =
        renderer.renderIcebergMetadataQuery(
            dataset =
                EventLogDuckDbIcebergMetadataDataset(
                    metadataLocation = "s3://warehouse/event-log/metadata/v1.metadata.json",
                ),
            filter = EventLogDuckDbFilter(serviceId = "totp"),
        )

    assertTrue(
        sql.contains("FROM iceberg_scan('s3://warehouse/event-log/metadata/v1.metadata.json')"))
    assertTrue(sql.contains("service_id = 'totp'"))
  }

  @Test
  fun `iceberg attach and summary query are rendered`() {
    val attach =
        renderer.renderIcebergAttach(
            EventLogDuckDbIcebergCatalog(
                alias = "lake",
                source = "warehouse",
                endpoint = "https://catalog.example.com",
            ))
    val summary =
        renderer.renderEventCountQuery(
            catalog = EventLogDuckDbIcebergCatalog(alias = "lake"),
            tableId =
                EventLogIcebergTableId(namespace = listOf("lakehouse"), tableName = "event_logs"),
            filter = EventLogDuckDbFilter(serviceId = "totp"),
        )

    assertTrue(attach.contains("TYPE iceberg"))
    assertTrue(attach.contains("ENDPOINT 'https://catalog.example.com'"))
    assertTrue(summary.contains("COUNT(*) AS event_count"))
    assertTrue(summary.contains("GROUP BY event_name"))
  }

  @Test
  fun `client ga4 compat parquet view renders ga style aliases`() {
    val sql =
        renderer.renderCreateClientGa4CompatParquetView(
            dataset = EventLogDuckDbParquetDataset(rootUri = "s3://bucket/event-log"),
            filter =
                EventLogDuckDbClientFilter(
                    serviceId = "fillingheart",
                    platform = EventLogPlatform.CLIENT_DESKTOP,
                    appId = "fillingheart.windows",
                    screenName = "home",
                    dateFrom = "2026-04-10",
                    dateTo = "2026-04-11",
                ),
        )

    assertTrue(sql.contains("CREATE OR REPLACE VIEW client_ga4_compat_events_v1 AS"))
    assertTrue(sql.contains("actor_id AS user_id"))
    assertTrue(sql.contains("userPseudoId AS user_pseudo_id"))
    assertTrue(sql.contains("sessionId AS session_id"))
    assertTrue(sql.contains("COALESCE(engagementTimeMsec, 0) AS engagement_time_msec"))
    assertTrue(sql.contains("appId = 'fillingheart.windows'"))
    assertTrue(sql.contains("screenName = 'home'"))
  }

  @Test
  fun `client ga4 compat parquet view always renders a client platform where clause`() {
    val sql =
        renderer.renderCreateClientGa4CompatParquetView(
            dataset = EventLogDuckDbParquetDataset(rootUri = "s3://bucket/event-log"))

    assertTrue(
        sql.contains(
            "WHERE platform IN ('client_web', 'client_mobile', 'client_tablet', 'client_ipad', 'client_desktop')"))
  }

  @Test
  fun `client analytics queries render active users and sessions`() {
    val activeUsers = renderer.renderClientDailyActiveUsersQuery()
    val activeInstalls = renderer.renderClientActiveInstallsQuery()
    val sessions = renderer.renderClientSessionCountQuery()
    val engagement = renderer.renderClientEngagementTimeQuery()

    assertTrue(
        activeUsers.contains("COUNT(DISTINCT COALESCE(user_id, user_pseudo_id)) AS active_users"))
    assertTrue(activeUsers.contains("GROUP BY event_date"))
    assertTrue(activeInstalls.contains("COUNT(DISTINCT user_pseudo_id) AS active_installs"))
    assertTrue(
        sessions.contains(
            "COUNT(DISTINCT CONCAT(user_pseudo_id, '#', session_id)) AS session_count"))
    assertTrue(engagement.contains("SUM(engagement_time_msec) AS engagement_time_msec"))
  }

  @Test
  fun `client analytics queries render screen release and error summaries`() {
    val screenViews = renderer.renderClientScreenViewCountQuery()
    val releaseAdoption = renderer.renderClientReleaseAdoptionQuery()
    val errors = renderer.renderClientErrorCountQuery()

    assertTrue(screenViews.contains("event_name = 'screen_view'"))
    assertTrue(screenViews.contains("GROUP BY screen_name"))
    assertTrue(releaseAdoption.contains("COUNT(DISTINCT user_pseudo_id) AS active_installs"))
    assertTrue(releaseAdoption.contains("GROUP BY app_version, release_channel"))
    assertTrue(errors.contains("event_type = 'ERROR'"))
    assertTrue(errors.contains("COUNT(*) AS error_count"))
  }

  @Test
  fun `client ga4 compat iceberg views are rendered for metadata and rest catalog`() {
    val metadataView =
        renderer.renderCreateClientGa4CompatIcebergMetadataView(
            dataset =
                EventLogDuckDbIcebergMetadataDataset(
                    metadataLocation = "s3://warehouse/event-log/metadata/v1.metadata.json",
                ))
    val restView =
        renderer.renderCreateClientGa4CompatIcebergView(
            catalog = EventLogDuckDbIcebergCatalog(alias = "lake"),
            tableId =
                EventLogIcebergTableId(namespace = listOf("lakehouse"), tableName = "event_logs"),
            filter = EventLogDuckDbClientFilter(serviceId = "totp"),
        )

    assertTrue(
        metadataView.contains(
            "FROM iceberg_scan('s3://warehouse/event-log/metadata/v1.metadata.json')"))
    assertTrue(metadataView.contains("CREATE OR REPLACE VIEW client_ga4_compat_events_v1 AS"))
    assertTrue(restView.contains("FROM lake.lakehouse.event_logs"))
    assertTrue(restView.contains("service_id = 'totp'"))
  }
}
