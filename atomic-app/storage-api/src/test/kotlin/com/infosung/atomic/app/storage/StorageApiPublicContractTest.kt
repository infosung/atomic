package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.contract.response.BaseResponse
import java.lang.reflect.Modifier
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.ObjectMapper

class StorageApiPublicContractTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))

  @Test
  fun `image entity constructor contract should remain stable`() {
    val parameterNames =
        loadKClass("com.infosung.atomic.app.storage.adapter.out.persistence.ImageEntity")
            .primaryConstructor!!
            .parameters
            .mapNotNull { it.name }

    assertEquals(
        listOf(
            "id",
            "bucket",
            "serviceName",
            "storageService",
            "status",
            "uploaderId",
            "storageType",
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
            "createdAt",
        ),
        parameterNames,
    )
  }

  @Test
  fun `stored image constructor contract should remain stable`() {
    val parameterNames =
        loadKClass("com.infosung.atomic.app.storage.domain.StoredImage")
            .primaryConstructor!!
            .parameters
            .mapNotNull { it.name }

    assertEquals(
        listOf(
            "id",
            "bucket",
            "serviceName",
            "storageService",
            "status",
            "uploaderId",
            "storageType",
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
            "createdAt",
        ),
        parameterNames,
    )
  }

  @Test
  fun `image response constructor contract should remain stable`() {
    val parameterNames =
        loadKClass("com.infosung.atomic.app.storage.adapter.in.web.ImageResponse")
            .primaryConstructor!!
            .parameters
            .mapNotNull { it.name }

    assertEquals(
        listOf(
            "id",
            "bucket",
            "serviceName",
            "storageService",
            "status",
            "uploaderId",
            "storageType",
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
            "createdAt",
        ),
        parameterNames,
    )
  }

  @Test
  fun `image delete pending snapshot constructor contract should remain stable`() {
    val parameterNames =
        loadKClass("com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot")
            .primaryConstructor!!
            .parameters
            .mapNotNull { it.name }

    assertEquals(
        listOf(
            "pendingCount",
            "oldestPendingCreatedAt",
        ),
        parameterNames,
    )
  }

  @Test
  fun `image delete recovery result constructor contract should remain stable`() {
    val parameterNames =
        loadKClass("com.infosung.atomic.app.storage.domain.ImageDeleteRecoveryResult")
            .primaryConstructor!!
            .parameters
            .mapNotNull { it.name }

    assertEquals(
        listOf(
            "scannedCount",
            "recoveredCount",
            "failedCount",
            "remainingPendingCount",
            "oldestPendingCreatedAt",
        ),
        parameterNames,
    )
  }

  @Test
  fun `atomic app image properties public keys should remain stable`() {
    assertEquals(
        listOf(
                "defaultQuality",
                "enabled",
                "endpointPath",
                "maxQuality",
                "minQuality",
                "thumbnailEnabled",
                "uploaderParameterEnabled",
                "uploaderParameterName",
            )
            .sorted(),
        AtomicAppImageProperties::class.memberProperties.map { it.name }.sorted(),
    )
  }

  @Test
  fun `storage use case seams should remain stable`() {
    assertEquals(
        listOf("uploadImage(UploadAppImageCommand):StoredImage"),
        publicSignatures(
            loadClass("com.infosung.atomic.app.storage.application.port.in.UploadAppImageUseCase")),
    )
    assertEquals(
        listOf("deleteImage(DeleteAppImageCommand):void"),
        publicSignatures(
            loadClass("com.infosung.atomic.app.storage.application.port.in.DeleteAppImageUseCase")),
    )
    assertEquals(
        listOf("inspectDeletePendingImages():ImageDeletePendingSnapshot"),
        publicSignatures(
            loadClass(
                "com.infosung.atomic.app.storage.application.port.in.InspectDeletePendingImagesUseCase")),
    )
    assertEquals(
        listOf("recoverDeletePendingImages(int):ImageDeleteRecoveryResult"),
        publicSignatures(
            loadClass(
                "com.infosung.atomic.app.storage.application.port.in.RecoverDeletePendingImagesUseCase")),
    )
  }

  @Test
  fun `app storage controller upload response type should use image response dto`() {
    val uploadImageMethod =
        loadKClass("com.infosung.atomic.app.storage.adapter.in.web.AppStorageController")
            .declaredFunctions
            .first { it.name == "uploadImage" }
    val responseType = uploadImageMethod.returnType

    assertEquals(BaseResponse::class, responseType.classifier)
    assertEquals(
        loadKClass("com.infosung.atomic.app.storage.adapter.in.web.ImageResponse"),
        responseType.arguments.single().type?.classifier,
    )
  }

  @Test
  fun `base response with image response should serialize with stable boot json contract`() {
    contextRunner.run { context ->
      val objectMapper = context.getBean(ObjectMapper::class.java)
      val imageResponseType =
          loadKClass("com.infosung.atomic.app.storage.adapter.in.web.ImageResponse")
      val constructor = imageResponseType.primaryConstructor!!
      val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111")
      val response =
          BaseResponse.ok(
              constructor.callBy(
                  mapOf(
                      constructor.parameters.first { it.name == "id" } to imageId,
                      constructor.parameters.first { it.name == "bucket" } to "image-bucket",
                      constructor.parameters.first { it.name == "serviceName" } to "gallery",
                      constructor.parameters.first { it.name == "storageService" } to "cdn",
                      constructor.parameters.first { it.name == "status" } to "ACTIVE",
                      constructor.parameters.first { it.name == "uploaderId" } to "member-1",
                      constructor.parameters.first { it.name == "storageType" } to "R2",
                      constructor.parameters.first { it.name == "fileName" } to
                          "image-bucket/original.webp",
                      constructor.parameters.first { it.name == "thumbnailFileName" } to
                          "image-bucket/thumb.webp",
                      constructor.parameters.first { it.name == "url" } to
                          "https://cdn.example.com/original.webp",
                      constructor.parameters.first { it.name == "thumbnailUrl" } to
                          "https://cdn.example.com/thumb.webp",
                      constructor.parameters.first { it.name == "width" } to 640,
                      constructor.parameters.first { it.name == "height" } to 480,
                      constructor.parameters.first { it.name == "fileSize" } to 12345L,
                      constructor.parameters.first { it.name == "thumbnailWidth" } to 320,
                      constructor.parameters.first { it.name == "thumbnailHeight" } to 240,
                      constructor.parameters.first { it.name == "thumbnailFileSize" } to 4567L,
                      constructor.parameters.first { it.name == "createdAt" } to
                          LocalDateTime.of(2024, 1, 2, 3, 4, 5),
                  ),
              ),
          )

      val json = objectMapper.readTree(objectMapper.writeValueAsString(response))

      assertEquals("OK", json["code"].stringValue())
      assertEquals("Success", json["message"].stringValue())
      assertEquals(imageId.toString(), json["data"]["id"].stringValue())
      assertEquals("gallery", json["data"]["serviceName"].stringValue())
      assertEquals("cdn", json["data"]["storageService"].stringValue())
      assertEquals("member-1", json["data"]["uploaderId"].stringValue())
      assertEquals("R2", json["data"]["storageType"].stringValue())
      assertEquals("image-bucket/original.webp", json["data"]["fileName"].stringValue())
      assertEquals("image-bucket/thumb.webp", json["data"]["thumbnailFileName"].stringValue())
      assertEquals("2024-01-02T03:04:05", json["data"]["createdAt"].stringValue())
      assertTrue(json["data"].has("thumbnailUrl"))
      assertTrue(json["data"].has("thumbnailFileSize"))
    }
  }

  private fun loadClass(name: String): Class<*> = Class.forName(name)

  private fun loadKClass(name: String): KClass<*> = loadClass(name).kotlin

  private fun publicSignatures(type: Class<*>): List<String> {
    return type.declaredMethods
        .filter {
          Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("\$default")
        }
        .map { method ->
          val parameters = method.parameterTypes.joinToString(", ") { it.simpleName }
          "${method.name}($parameters):${method.returnType.simpleName}"
        }
        .sorted()
  }
}
