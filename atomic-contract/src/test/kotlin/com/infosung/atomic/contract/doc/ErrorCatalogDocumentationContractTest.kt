package com.infosung.atomic.contract.doc

import com.infosung.atomic.app.oauth.application.exception.OauthRedirectErrorCode
import com.infosung.atomic.app.storage.application.exception.StorageErrorCode
import com.infosung.atomic.app.version.application.exception.AppVersionErrorCode
import com.infosung.atomic.spring.idempotency.IdempotencyErrorCode
import com.infosung.atomic.spring.security.SecurityErrorCode
import com.infosung.atomic.spring.web.ratelimit.RateLimitErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorCatalogDocumentationContractTest {
  @Test
  fun `atomic app usage guide should document app module error catalogs`() {
    val markdown = DocumentationContractFixtures.read("docs/usage/atomic-app.md")

    assertRows(markdown, AppVersionErrorCode.entries.map { it.toRow() })
    assertRows(markdown, StorageErrorCode.entries.map { it.toRow() })
    assertRows(markdown, OauthRedirectErrorCode.entries.map { it.toRow() })
  }

  @Test
  fun `module usage guides should document non mvc error catalogs`() {
    val securityDoc = DocumentationContractFixtures.read("docs/usage/atomic-spring-security.md")
    val rateLimitDoc = DocumentationContractFixtures.read("docs/usage/atomic-spring-web.md")
    val idempotencyDoc =
        DocumentationContractFixtures.read("docs/usage/atomic-spring-idempotency.md")

    assertRows(
        securityDoc,
        SecurityErrorCode.entries.map { it.toRow() },
        expectedColumnCount = 4,
    )
    assertRows(
        rateLimitDoc,
        RateLimitErrorCode.entries.map { it.toRow() },
        expectedColumnCount = 4,
    )
    assertRows(
        idempotencyDoc,
        IdempotencyErrorCode.entries.map { it.toRow() },
        expectedColumnCount = 4,
    )
  }

  @Test
  fun `migration guide should document all stable error catalogs`() {
    val markdown = DocumentationContractFixtures.read("docs/migration/v0.0.4-to-v0.0.5.md")

    assertRows(markdown, AppVersionErrorCode.entries.map { it.toRow() })
    assertRows(markdown, StorageErrorCode.entries.map { it.toRow() })
    assertRows(markdown, OauthRedirectErrorCode.entries.map { it.toRow() })

    assertAreaRows(
        markdown = markdown,
        area = "security",
        rows = SecurityErrorCode.entries.map { it.toRow() },
    )
    assertAreaRows(
        markdown = markdown,
        area = "rate-limit",
        rows = RateLimitErrorCode.entries.map { it.toRow() },
    )
    assertAreaRows(
        markdown = markdown,
        area = "idempotency",
        rows = IdempotencyErrorCode.entries.map { it.toRow() },
    )
  }

  private fun assertRows(
      markdown: String,
      rows: List<ErrorCatalogRow>,
      expectedColumnCount: Int = 3,
  ) {
    rows.forEach { row ->
      val matchingLines =
          markdown.lineSequence().filter { it.trim().startsWith(row.standardPrefix()) }.toList()

      assertEquals(
          1,
          matchingLines.size,
          "Expected exactly one documented row for ${row.code}.",
      )
      assertEquals(
          expectedColumnCount,
          columnCount(matchingLines.single()),
          "Unexpected column count for ${row.code}.",
      )
    }
  }

  private fun assertAreaRows(
      markdown: String,
      area: String,
      rows: List<ErrorCatalogRow>,
  ) {
    rows.forEach { row ->
      val expectedPrefix = "| $area | `${row.code}` | `${row.status}` | `${row.message}` |"
      val matchingLines =
          markdown.lineSequence().filter { it.trim().startsWith(expectedPrefix) }.toList()

      assertEquals(
          1,
          matchingLines.size,
          "Expected exactly one migration row for $area/${row.code}.",
      )
      assertTrue(
          columnCount(matchingLines.single()) >= 4,
          "Expected area row for $area/${row.code} to keep the area column.",
      )
    }
  }

  private fun columnCount(row: String): Int =
      row.trim().trim('|').split('|').map { it.trim() }.count { it.isNotEmpty() }

  private fun ErrorCatalogRow.standardPrefix(): String = "| `$code` | `$status` | `$message` |"

  private fun AppVersionErrorCode.toRow() = ErrorCatalogRow(name, defaultHttpStatus, defaultMessage)

  private fun StorageErrorCode.toRow() = ErrorCatalogRow(name, defaultHttpStatus, defaultMessage)

  private fun OauthRedirectErrorCode.toRow() =
      ErrorCatalogRow(name, defaultHttpStatus, defaultMessage)

  private fun SecurityErrorCode.toRow() = ErrorCatalogRow(name, defaultHttpStatus, defaultMessage)

  private fun RateLimitErrorCode.toRow() = ErrorCatalogRow(name, defaultHttpStatus, defaultMessage)

  private fun IdempotencyErrorCode.toRow() =
      ErrorCatalogRow(name, defaultHttpStatus, defaultMessage)
}

private data class ErrorCatalogRow(
    val code: String,
    val status: Int,
    val message: String,
)
