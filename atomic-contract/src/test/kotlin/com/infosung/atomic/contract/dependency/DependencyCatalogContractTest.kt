package com.infosung.atomic.contract.dependency

import com.infosung.atomic.contract.doc.DocumentationContractFixtures
import kotlin.test.Test
import kotlin.test.assertTrue

class DependencyCatalogContractTest {
  private companion object {
    const val SKIP_SECURE_BASELINE_CHECK_PROPERTY = "atomic.contract.skipSecureSpringBootBaseline"
    const val SKIP_SECURE_BASELINE_CHECK_ENV = "ATOMIC_CONTRACT_SKIP_SECURE_SPRING_BOOT_BASELINE"

    val secureSpringBootLinePattern =
        Regex("""^\s*springboot\s*=\s*"4\.0\.(\d+)"\s*$""", RegexOption.MULTILINE)
  }

  @Test
  fun `spring boot catalog version should stay on the fixed 4_0_x patch line`() {
    if (shouldSkipSecureBaselineCheck()) {
      return
    }

    val catalog = DocumentationContractFixtures.read("gradle/libs.versions.toml")
    val patchVersion =
        secureSpringBootLinePattern.find(catalog)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Failed to read springboot version from gradle/libs.versions.toml")

    assertTrue(
        patchVersion >= 4,
        "springboot catalog version should stay on 4.0.4+ to include the Spring security fixes",
    )
  }

  private fun shouldSkipSecureBaselineCheck(): Boolean {
    return System.getProperty(SKIP_SECURE_BASELINE_CHECK_PROPERTY) == "true" ||
        System.getenv(SKIP_SECURE_BASELINE_CHECK_ENV) == "true"
  }
}
