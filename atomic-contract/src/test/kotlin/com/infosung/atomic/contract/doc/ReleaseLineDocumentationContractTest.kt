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
