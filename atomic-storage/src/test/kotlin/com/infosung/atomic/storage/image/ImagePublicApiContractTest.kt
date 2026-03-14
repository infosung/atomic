package com.infosung.atomic.storage.image

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

class ImagePublicApiContractTest {
  @Test
  fun `image model field names should remain stable`() {
    assertEquals(
        listOf("width", "height", "size"),
        declaredFieldNames(ImageMetadata::class.java),
    )
    assertEquals(
        listOf("fileName", "width", "height", "size"),
        declaredFieldNames(ImageFileInfo::class.java),
    )
    assertEquals(
        listOf(
            "storageType",
            "bucket",
            "storageObjectKey",
            "storageThumbnailObjectKey",
            "fileName",
            "thumbnailFileName",
            "url",
            "thumbnailUrl",
            "width",
            "height",
            "fileSize",
            "thumbnailWidth",
            "thumbnailHeight",
            "thumbnailFileSize",
            "thumbnailUploadFailed",
            "thumbnailFailureReason",
        ),
        declaredFieldNames(ImageUploadResult::class.java),
    )
  }

  @Test
  fun `image service public methods should remain stable`() {
    assertEquals(
        listOf(
            "deleteImage(String, String, String):void",
            "uploadImage(File, String, String, double):ImageUploadResult",
            "uploadImage(InputStream, String, String, double):ImageUploadResult",
        ),
        ImageService::class.java.declaredMethods
            .filter {
              Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("\$default")
            }
            .map { method ->
              val parameters = method.parameterTypes.joinToString(", ") { it.simpleName }
              "${method.name}($parameters):${method.returnType.simpleName}"
            }
            .sorted(),
    )
  }

  private fun declaredFieldNames(type: Class<*>): List<String> {
    return type.declaredFields.filterNot { it.isSynthetic }.map { it.name }
  }
}
