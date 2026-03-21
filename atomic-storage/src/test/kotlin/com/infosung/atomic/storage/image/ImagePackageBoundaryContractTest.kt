package com.infosung.atomic.storage.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ImagePackageBoundaryContractTest {
  @Test
  fun `image module should expose public facade internal application split and spi package`() {
    assertEquals(
        "com.infosung.atomic.storage.image.ImageService",
        ImageService::class.java.name,
    )
    assertNotNull(
        Class.forName("com.infosung.atomic.storage.image.application.service.UploadImageService"),
    )
    assertNotNull(
        Class.forName("com.infosung.atomic.storage.image.application.service.DeleteImageService"),
    )
    assertNotNull(
        Class.forName(
            "com.infosung.atomic.storage.image.application.support.ImageStorageAccessSupport"),
    )
    assertNotNull(
        Class.forName("com.infosung.atomic.storage.image.domain.ResolvedImageStorageAccess"),
    )
    assertNotNull(
        Class.forName("com.infosung.atomic.storage.image.spi.ImageObjectKeyGenerator"),
    )
    assertNotNull(
        Class.forName("com.infosung.atomic.storage.image.spi.ImageInputValidator"),
    )
    assertNotNull(
        Class.forName("com.infosung.atomic.storage.image.spi.ImageMetadataReader"),
    )
    assertNotNull(
        Class.forName("com.infosung.atomic.storage.image.spi.ImageThumbnailGenerator"),
    )
  }

  @Test
  fun `advanced image strategy seams should no longer be exported from the root image package`() {
    assertFailsWith<ClassNotFoundException> {
      Class.forName("com.infosung.atomic.storage.image.ImageObjectKeyGenerator")
    }
    assertFailsWith<ClassNotFoundException> {
      Class.forName("com.infosung.atomic.storage.image.ImageInputValidator")
    }
    assertFailsWith<ClassNotFoundException> {
      Class.forName("com.infosung.atomic.storage.image.ImageMetadataReader")
    }
    assertFailsWith<ClassNotFoundException> {
      Class.forName("com.infosung.atomic.storage.image.ImageThumbnailGenerator")
    }
  }
}
