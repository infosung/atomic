package com.infosung.atomic.app.storage

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class StoragePackageBoundaryContractTest {
  @Test
  fun `storage legal topology classes should exist in adapter application and domain packages`() {
    assertNotNull(
        Class.forName("com.infosung.atomic.app.storage.adapter.in.web.AppStorageController"))
    assertNotNull(
        Class.forName(
            "com.infosung.atomic.app.storage.adapter.in.web.AppStorageHttpExceptionHandler"),
    )
    assertNotNull(Class.forName("com.infosung.atomic.app.storage.adapter.in.web.ImageResponse"))
    assertNotNull(
        Class.forName("com.infosung.atomic.app.storage.application.port.in.UploadAppImageUseCase"))
    assertNotNull(
        Class.forName("com.infosung.atomic.app.storage.application.port.in.DeleteAppImageUseCase"))
    assertNotNull(
        Class.forName(
            "com.infosung.atomic.app.storage.application.port.in.InspectDeletePendingImagesUseCase",
        ),
    )
    assertNotNull(
        Class.forName(
            "com.infosung.atomic.app.storage.application.port.in.RecoverDeletePendingImagesUseCase",
        ),
    )
    assertNotNull(Class.forName("com.infosung.atomic.app.storage.domain.StoredImage"))
    assertNotNull(
        Class.forName("com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot"))
    assertNotNull(Class.forName("com.infosung.atomic.app.storage.domain.ImageDeleteRecoveryResult"))
    assertNotNull(
        Class.forName("com.infosung.atomic.app.storage.adapter.out.persistence.ImageEntity"),
    )
    assertNotNull(
        Class.forName("com.infosung.atomic.app.storage.adapter.out.persistence.ImageRepository"),
    )
  }

  @Test
  fun `legacy root storage implementation types should be removed`() {
    assertMissing("com.infosung.atomic.app.storage.AppImageApiService")
    assertMissing("com.infosung.atomic.app.storage.AppImageDeleteRecoveryService")
    assertMissing("com.infosung.atomic.app.storage.AppImageEntityTxService")
    assertMissing("com.infosung.atomic.app.storage.AppStorageController")
    assertMissing("com.infosung.atomic.app.storage.AppStorageHttpExceptionHandler")
    assertMissing("com.infosung.atomic.app.storage.ImageEntity")
    assertMissing("com.infosung.atomic.app.storage.ImageRepository")
    assertMissing("com.infosung.atomic.app.storage.ImageResponse")
  }

  private fun assertMissing(name: String) {
    assertFailsWith<ClassNotFoundException> { Class.forName(name) }
  }
}
