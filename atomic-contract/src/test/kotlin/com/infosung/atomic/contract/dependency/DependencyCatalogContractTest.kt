package com.infosung.atomic.contract.dependency

import com.infosung.atomic.contract.doc.DocumentationContractFixtures
import kotlin.test.Test
import kotlin.test.assertTrue

class DependencyCatalogContractTest {
  @Test
  fun `spring boot catalog version should stay on the fixed 4_0_x patch line`() {
    val catalog = DocumentationContractFixtures.read("gradle/libs.versions.toml")
    val patchVersion =
        Regex("""^springboot = "4\.0\.(\d+)"$""", RegexOption.MULTILINE)
            .find(catalog)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: error("Failed to read springboot version from gradle/libs.versions.toml")

    assertTrue(
        patchVersion >= 4,
        "springboot catalog version should stay on 4.0.4+ to include the Spring security fixes",
    )
  }
}
