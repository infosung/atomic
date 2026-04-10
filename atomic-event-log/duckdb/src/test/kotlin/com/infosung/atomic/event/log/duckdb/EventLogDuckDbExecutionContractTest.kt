package com.infosung.atomic.event.log.duckdb

import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbClientFilter
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbParquetDataset
import com.infosung.atomic.event.log.duckdb.adapter.out.EventLogDuckDbSqlRenderer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class EventLogDuckDbExecutionContractTest {
  @TempDir lateinit var tempDir: Path

  private val renderer = EventLogDuckDbSqlRenderer()

  @Test
  fun `ga compatible parquet view and canned analytics execute against canonical parquet layout`() {
    val root = tempDir.resolve("event-log")
    val parquetDirectory =
        root.resolve(
            "service_id=fillingheart/platform=client_desktop/dt=2026-04-10/hour=10/server_id=srv-1/boot_id=boot-1")
    Files.createDirectories(parquetDirectory)
    val parquetFile = parquetDirectory.resolve("flush_seq=1.parquet")

    DriverManager.getConnection("jdbc:duckdb:").use { connection ->
      connection.createStatement().use { statement ->
        statement.execute(
            """
            CREATE TABLE source (
              event_name VARCHAR,
              event_type VARCHAR,
              occurred_at TIMESTAMP,
              received_at TIMESTAMP,
              actor_id VARCHAR,
              userPseudoId VARCHAR,
              sessionId BIGINT,
              engagementTimeMsec BIGINT,
              appId VARCHAR,
              appVersion VARCHAR,
              screenName VARCHAR,
              releaseChannel VARCHAR,
              buildNumber VARCHAR,
              locale VARCHAR,
              timezone VARCHAR,
              deviceCategory VARCHAR,
              deviceLanguage VARCHAR,
              operatingSystem VARCHAR,
              operatingSystemVersion VARCHAR,
              deviceModel VARCHAR,
              deviceBrand VARCHAR,
              browser VARCHAR,
              browserVersion VARCHAR,
              screenResolution VARCHAR,
              trace_id VARCHAR,
              tags VARCHAR
            )
            """
                .trimIndent())
        statement.execute(
            """
            INSERT INTO source VALUES
              ('screen_view', 'ACTION', TIMESTAMP '2026-04-10 10:15:30', TIMESTAMP '2026-04-10 10:15:31', 'user-1', 'pseudo-1', 1712744100, 5000, 'fillingheart.windows', '1.2.3', 'home', 'stable', '1203', 'ko-KR', 'Asia/Seoul', 'desktop', 'ko', 'Windows', '11', 'Surface', 'Microsoft', 'Edge', '135.0', '1920x1080', 'trace-1', 'client'),
              ('app_exception', 'ERROR', TIMESTAMP '2026-04-10 10:16:30', TIMESTAMP '2026-04-10 10:16:31', 'user-1', 'pseudo-1', 1712744100, 100, 'fillingheart.windows', '1.2.3', 'home', 'stable', '1203', 'ko-KR', 'Asia/Seoul', 'desktop', 'ko', 'Windows', '11', 'Surface', 'Microsoft', 'Edge', '135.0', '1920x1080', 'trace-2', 'client')
            """
                .trimIndent())
        statement.execute(
            "COPY source TO '${escapeSqlLiteral(parquetFile.toString())}' (FORMAT PARQUET)")

        statement.execute(
            renderer.renderCreateClientGa4CompatParquetView(
                dataset = EventLogDuckDbParquetDataset(rootUri = root.toString()),
                filter =
                    EventLogDuckDbClientFilter(
                        serviceId = "fillingheart",
                        platform = EventLogPlatform.CLIENT_DESKTOP,
                        appId = "fillingheart.windows",
                    ),
            ))

        statement
            .executeQuery(
                "SELECT user_id, user_pseudo_id, screen_name, engagement_time_msec FROM client_ga4_compat_events_v1 ORDER BY event_name ASC")
            .use { resultSet ->
              resultSet.next()
              assertEquals("user-1", resultSet.getString("user_id"))
              assertEquals("pseudo-1", resultSet.getString("user_pseudo_id"))
              assertEquals("home", resultSet.getString("screen_name"))
              assertEquals(100L, resultSet.getLong("engagement_time_msec"))

              resultSet.next()
              assertEquals("user-1", resultSet.getString("user_id"))
              assertEquals("pseudo-1", resultSet.getString("user_pseudo_id"))
              assertEquals("home", resultSet.getString("screen_name"))
              assertEquals(5000L, resultSet.getLong("engagement_time_msec"))
            }

        statement.executeQuery(renderer.renderClientDailyActiveUsersQuery()).use { resultSet ->
          resultSet.next()
          assertEquals("20260410", resultSet.getString("event_date"))
          assertEquals(1L, resultSet.getLong("active_users"))
        }

        statement.executeQuery(renderer.renderClientSessionCountQuery()).use { resultSet ->
          resultSet.next()
          assertEquals(1L, resultSet.getLong("session_count"))
        }

        statement.executeQuery(renderer.renderClientScreenViewCountQuery()).use { resultSet ->
          resultSet.next()
          assertEquals("home", resultSet.getString("screen_name"))
          assertEquals(1L, resultSet.getLong("screen_view_count"))
        }

        statement.executeQuery(renderer.renderClientErrorCountQuery()).use { resultSet ->
          resultSet.next()
          assertEquals("20260410", resultSet.getString("event_date"))
          assertEquals(1L, resultSet.getLong("error_count"))
        }

        statement.executeQuery(renderer.renderClientEngagementTimeQuery()).use { resultSet ->
          resultSet.next()
          assertEquals("20260410", resultSet.getString("event_date"))
          assertEquals(5100L, resultSet.getLong("engagement_time_msec"))
        }
      }
    }
  }

  private fun escapeSqlLiteral(value: String): String = value.replace("'", "''")
}
