package com.infosung.atomic.storage.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImagePackageBoundaryContractTest {
  @Test
  fun `image module should expose public facade and internal application split`() {
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
  }
}
