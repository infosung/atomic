package com.infosung.atomic.contract.workflow

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CiWorkflowContractTest {
  private companion object {
    val workflow by lazy { WorkflowContractFixtures.readWorkflow(".github/workflows/ci.yml") }
    val verifyJob by lazy { WorkflowContractFixtures.readCiVerifyJob(".github/workflows/ci.yml") }
  }

  @Test
  fun `ci workflow should keep existing compatibility lanes and add previous latest stable lanes`() {
    val lanes = verifyJob.include
    assertTrue(
        lanes.any { it.springboot == "4.0.5" && it.kotlin == "2.3.10" && it.label == "baseline" },
        "ci workflow should keep the secure baseline Spring Boot lane",
    )
    assertTrue(
        lanes.any {
          it.springboot == "4.0.4" && it.kotlin == "2.3.10" && it.label == "previous-stable-boot"
        },
        "ci workflow should include the previous stable Spring Boot patch lane",
    )
    assertTrue(
        lanes.any {
          it.springboot == "4.0.3" && it.kotlin == "2.3.10" && it.label == "legacy-compat"
        },
        "ci workflow should keep the first legacy Spring Boot compatibility lane",
    )
    assertTrue(
        lanes.any {
          it.springboot == "4.0.2" && it.kotlin == "2.3.10" && it.label == "legacy-compat"
        },
        "ci workflow should keep the second legacy Spring Boot compatibility lane",
    )
    assertTrue(
        lanes.any {
          it.springboot == "4.0.1" && it.kotlin == "2.3.10" && it.label == "legacy-compat"
        },
        "ci workflow should keep the third legacy Spring Boot compatibility lane",
    )
    assertTrue(
        lanes.any {
          it.springboot == "4.0.5" && it.kotlin == "2.3.10" && it.label == "latest-boot"
        },
        "ci workflow should keep the latest stable Spring Boot lane",
    )
    assertTrue(
        lanes.any { it.springboot == "4.0.5" && it.kotlin == "2.3.0" && it.label == "compat" },
        "ci workflow should keep the Kotlin compatibility lane on the secure Spring Boot baseline",
    )
    assertTrue(
        lanes.any { it.kotlin == "2.3.10" && it.springboot == "4.0.5" },
        "ci workflow should keep the current catalog Kotlin lane",
    )
    assertTrue(
        lanes.any {
          it.springboot == "4.0.5" && it.kotlin == "2.3.20" && it.label == "latest-kotlin"
        },
        "ci workflow should keep the latest stable Kotlin lane",
    )
    assertTrue(
        lanes.any {
          it.springboot == "4.0.5" && it.kotlin == "2.3.20" && it.label == "latest-stable"
        },
        "ci workflow should include a latest stable verification lane",
    )
  }

  @Test
  fun `ci workflow should keep explicit static matrix instead of dynamic version resolution`() {
    val labels = verifyJob.include.map { it.label }.toSet()
    assertTrue(
        "baseline" in labels,
        "ci workflow should keep the baseline verification lane",
    )
    assertTrue(
        "compat" in labels,
        "ci workflow should keep the compatibility verification lanes",
    )
    assertTrue(
        "legacy-compat" in labels,
        "ci workflow should keep the legacy compatibility verification lanes",
    )
    assertTrue(
        "previous-stable-boot" in labels,
        "ci workflow should verify the previous Spring Boot patch release",
    )
    assertTrue(
        "latest-boot" in labels,
        "ci workflow should verify the latest Spring Boot against the catalog Kotlin version",
    )
    assertTrue(
        "latest-kotlin" in labels,
        "ci workflow should verify the latest Kotlin against the catalog Spring Boot version",
    )
    assertTrue(
        verifyJob.steps.any {
          it.name == "Verify baseline formatting and tests" &&
              it.ifExpression == "matrix.label == 'baseline'" &&
              it.run == "./gradlew spotlessCheck test"
        },
        "ci workflow should keep the original baseline formatting and test verification",
    )
    assertTrue(
        verifyJob.steps.any {
          it.name == "Verify compatibility tests" &&
              it.ifExpression == "matrix.label != 'baseline'" &&
              it.run == "./gradlew test -Datomic.contract.skipSecureSpringBootBaseline=true"
        },
        "ci workflow should verify compatibility tests for every non-baseline matrix lane while preserving the secure catalog baseline contract for committed sources",
    )
    assertFalse(
        workflow.contains("resolve-verify-matrix:"),
        "ci workflow should not use a separate dynamic matrix resolution job",
    )
    assertFalse(
        workflow.contains("repo.maven.apache.org"),
        "ci workflow should not resolve versions dynamically from Maven metadata",
    )
    assertFalse(
        workflow.contains("fromJson("),
        "ci workflow should keep an explicit static matrix",
    )
  }
}
