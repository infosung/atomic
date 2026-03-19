package com.infosung.atomic.app.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VersionPackageBoundaryContractTest {
  @Test
  fun `version root web seams should delegate into adapter in web package`() {
    val controllerAdapterField = AppVersionController::class.java.getDeclaredField("webAdapter")
    val handlerAdapterField =
        AppVersionHttpExceptionHandler::class.java.getDeclaredField("webAdapter")

    assertEquals(
        "com.infosung.atomic.app.version.adapter.in.web",
        controllerAdapterField.type.packageName,
    )
    assertEquals(
        "com.infosung.atomic.app.version.adapter.in.web",
        handlerAdapterField.type.packageName,
    )
  }

  @Test
  fun `version root web entry types should remain exported compatibility seams`() {
    assertEquals(
        "com.infosung.atomic.app.version.AppVersionController",
        AppVersionController::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.version.AppVersionHttpExceptionHandler",
        AppVersionHttpExceptionHandler::class.java.name,
    )
    assertNotNull(AppVersionController::class.java.constructors.singleOrNull())
    assertTrue(AppVersionHttpExceptionHandler::class.java.declaredConstructors.isNotEmpty())
  }
}
