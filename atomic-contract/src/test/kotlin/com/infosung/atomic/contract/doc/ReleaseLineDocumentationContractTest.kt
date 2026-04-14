package com.infosung.atomic.contract.doc

import com.infosung.atomic.contract.workflow.WorkflowContractFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseLineDocumentationContractTest {
  @Test
  fun `publish module lists in readme and usage docs should match actual release order`() {
    val expectedModules =
        WorkflowContractFixtures.readPublishModules(".github/workflows/publish-maven-central.yml")

    assertEquals(
        expectedModules,
        readBulletListAfter(
            "README.md",
            "Current `.github/workflows/publish-maven-central.yml` publishes:",
        ),
    )
    assertEquals(
        expectedModules,
        readBulletListAfter(
            "docs/usage/overview.md",
            "Current `.github/workflows/publish-maven-central.yml` publishes:",
        ),
    )
    assertEquals(
        expectedModules,
        readBulletListAfter(
            "docs/usage/atomic-starter.md",
            "Current `.github/workflows/publish-maven-central.yml` publishes:",
        ),
    )
  }

  @Test
  fun `v0_0_5 to v0_1_1 migration guide should document db scope and oauth redirect persistence behavior`() {
    val markdown = DocumentationContractFixtures.read("docs/migration/v0.0.5-to-v0.1.1.md")

    listOf("`postgresql`", "`mysql`", "`mariadb`", "`oracle`", "`h2`").forEach { marker ->
      assertTrue(markdown.contains(marker), "migration guide should mention $marker")
    }
    assertTrue(markdown.contains("JPA-backed"))
    assertTrue(markdown.contains("legacy JDBC"))
    assertTrue(markdown.contains("oracle-compatibility.yml"))
    assertTrue(markdown.contains("Spring Boot `4.0.5`"))
    assertTrue(markdown.contains("Spring Boot `4.0.3`"))
    assertTrue(markdown.contains("CVE-2026-22732"))
    assertTrue(markdown.contains("CVE-2026-22735"))
    assertTrue(markdown.contains("release notes"))
  }

  @Test
  fun `v0_1_1 release notes should summarize compatibility and migration-significant changes`() {
    val markdown = DocumentationContractFixtures.read("docs/release-notes/v0.1.1.md")

    listOf(
            "Spring Boot `4.0.5`",
            "Spring Boot `4.0.3`",
            "CVE-2026-22732",
            "CVE-2026-22735",
            "atomic.event.log.ingest.api",
            "JPA-backed",
            "`postgresql`",
            "`mysql`",
            "`mariadb`",
            "`oracle`",
            "`h2`",
        )
        .forEach { marker ->
          assertTrue(markdown.contains(marker), "release notes should mention $marker")
        }
  }

  @Test
  fun `current release entry docs should point to v0_1_3 release notes and migration guide`() {
    val readme = DocumentationContractFixtures.read("README.md")
    val overview = DocumentationContractFixtures.read("docs/usage/overview.md")
    val quickStart = DocumentationContractFixtures.read("docs/usage/quick-start.md")
    val migrationIndex = DocumentationContractFixtures.read("docs/migration/v0.0.1-to-next.md")

    assertTrue(readme.contains("docs/release-notes/v0.1.3.md"))
    assertTrue(readme.contains("docs/migration/v0.1.2-to-v0.1.3.md"))
    assertTrue(readme.contains("docs/usage/atomic-crypto.md"))
    assertTrue(overview.contains("../release-notes/v0.1.3.md"))
    assertTrue(overview.contains("../migration/v0.1.2-to-v0.1.3.md"))
    assertTrue(quickStart.contains("../migration/v0.1.2-to-v0.1.3.md"))
    assertTrue(migrationIndex.contains("v0.1.2-to-v0.1.3.md"))
  }

  @Test
  fun `v0_1_3 release notes should summarize crypto and key-rotation scope`() {
    val markdown = DocumentationContractFixtures.read("docs/release-notes/v0.1.3.md")

    listOf(
            "0.1.3",
            "atomic.crypto",
            "key rotation",
            "previousAccessKeys",
            "previousRefreshKeys",
            "Spring Boot `4.0.5`",
            "docs/release-notes/v0.1.3.md",
        )
        .forEach { marker ->
          assertTrue(markdown.contains(marker), "release notes should mention $marker")
        }
  }

  @Test
  fun `v0_1_2 to v0_1_3 migration guide should document crypto module and security rotation`() {
    val markdown = DocumentationContractFixtures.read("docs/migration/v0.1.2-to-v0.1.3.md")

    listOf(
            "0.1.2",
            "0.1.3",
            "atomic.crypto",
            "previousAccessKeys",
            "previousRefreshKeys",
            "Spring Boot `4.0.5`",
            "actions/checkout@v5",
        )
        .forEach { marker ->
          assertTrue(markdown.contains(marker), "migration guide should mention $marker")
        }
  }

  @Test
  fun `v0_1_1 to v0_1_2 migration guide should describe patch scope and secure baseline continuity`() {
    val markdown = DocumentationContractFixtures.read("docs/migration/v0.1.1-to-v0.1.2.md")

    listOf(
            "0.1.1",
            "0.1.2",
            "Spring Boot `4.0.5`",
            "CVE-2026-22732",
            "CVE-2026-22735",
            "Node 24",
            "actions/checkout@v5",
            "docs/release-notes/v0.1.2.md",
            "`postgresql`",
            "`mysql`",
            "`mariadb`",
            "`oracle`",
            "`h2`",
            "JPA-backed",
            "legacy JDBC",
        )
        .forEach { marker ->
          assertTrue(
              markdown.contains(marker),
              "current patch migration guide should mention $marker",
          )
        }
  }

  @Test
  fun `v0_1_2 release notes should summarize patch release scope`() {
    val markdown = DocumentationContractFixtures.read("docs/release-notes/v0.1.2.md")

    listOf(
            "Spring Boot `4.0.5`",
            "0.1.2",
            "0.1.1",
            "Node 24",
            "actions/checkout@v5",
            "atomic.event.log.ingest.api",
            "JPA-backed",
            "`postgresql`",
            "`mysql`",
            "`mariadb`",
            "`oracle`",
            "`h2`",
            "v0.1.1` -> `v0.1.2",
        )
        .forEach { marker ->
          assertTrue(markdown.contains(marker), "current release notes should mention $marker")
        }
  }

  private fun readBulletListAfter(path: String, anchor: String): List<String> {
    val markdown = DocumentationContractFixtures.read(path)
    val lines =
        markdown
            .substringAfter(anchor)
            .lineSequence()
            .drop(1)
            .dropWhile { it.isBlank() }
            .takeWhile { it.startsWith("- ") }
            .toList()
    return lines.map { it.removePrefix("- ").trim().removeSurrounding("`") }
  }
}
