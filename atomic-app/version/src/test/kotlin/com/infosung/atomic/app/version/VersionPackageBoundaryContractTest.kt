package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionController
import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionHttpExceptionHandler
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionEntity
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VersionPackageBoundaryContractTest {
  @Test
  fun `version legal style topology should place web and persistence types in adapter packages`() {
    assertEquals(
        "com.infosung.atomic.app.version.adapter.in.web",
        AppVersionController::class.java.packageName,
    )
    assertEquals(
        "com.infosung.atomic.app.version.adapter.in.web",
        AppVersionHttpExceptionHandler::class.java.packageName,
    )
    assertEquals(
        "com.infosung.atomic.app.version.adapter.out.persistence",
        ServiceVersionEntity::class.java.packageName,
    )
    assertEquals(
        "com.infosung.atomic.app.version.adapter.out.persistence",
        ServiceVersionRepository::class.java.packageName,
    )
  }

  @Test
  fun `version adapter entry types should be exported from their target packages`() {
    assertEquals(
        "com.infosung.atomic.app.version.adapter.in.web.AppVersionController",
        AppVersionController::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.version.adapter.in.web.AppVersionHttpExceptionHandler",
        AppVersionHttpExceptionHandler::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionEntity",
        ServiceVersionEntity::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository",
        ServiceVersionRepository::class.java.name,
    )
    assertNotNull(AppVersionController::class.java.constructors.singleOrNull())
    assertTrue(AppVersionHttpExceptionHandler::class.java.declaredConstructors.isNotEmpty())
  }
}
