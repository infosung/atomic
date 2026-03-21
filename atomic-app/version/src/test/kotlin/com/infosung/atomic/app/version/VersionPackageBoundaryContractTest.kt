package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionController
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionEntity
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository
import com.infosung.atomic.app.version.application.exception.AppVersionApplicationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class VersionPackageBoundaryContractTest {
  @Test
  fun `version legal style topology should place web persistence and public exception types in stable packages`() {
    assertEquals(
        "com.infosung.atomic.app.version.adapter.in.web",
        AppVersionController::class.java.packageName,
    )
    assertEquals(
        "com.infosung.atomic.app.version.application.exception",
        AppVersionApplicationException::class.java.packageName,
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
        "com.infosung.atomic.app.version.application.exception.AppVersionApplicationException",
        AppVersionApplicationException::class.java.name,
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
  }

  @Test
  fun `legacy version module http advice should be removed`() {
    assertFailsWith<ClassNotFoundException> {
      Class.forName("com.infosung.atomic.app.version.adapter.in.web.AppVersionHttpExceptionHandler")
    }
  }
}
