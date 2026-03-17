package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.contract.response.BaseResponse
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.lang.reflect.Modifier
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes
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
    val parameterNames = ImageEntity::class.primaryConstructor!!.parameters.mapNotNull { it.name }

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
    val parameterNames = ImageResponse::class.primaryConstructor!!.parameters.mapNotNull { it.name }

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
  fun `image entity schema annotations should remain stable`() {
    val entity = ImageEntity::class.java.getAnnotation(Entity::class.java)
    val table = ImageEntity::class.java.getAnnotation(Table::class.java)
    val idField = ImageEntity::class.java.getDeclaredField("id")
    val jdbcTypeCode = idField.getAnnotation(JdbcTypeCode::class.java)
    val bucketField = ImageEntity::class.java.getDeclaredField("bucket")
    val serviceNameField = ImageEntity::class.java.getDeclaredField("serviceName")
    val storageServiceField = ImageEntity::class.java.getDeclaredField("storageService")
    val storageTypeField = ImageEntity::class.java.getDeclaredField("storageType")
    val fileNameField = ImageEntity::class.java.getDeclaredField("fileName")
    val thumbnailFileNameField = ImageEntity::class.java.getDeclaredField("thumbnailFileName")
    val urlField = ImageEntity::class.java.getDeclaredField("url")
    val thumbnailUrlField = ImageEntity::class.java.getDeclaredField("thumbnailUrl")

    assertEquals("image", entity.name)
    assertEquals("image", table.name)
    assertNotNull(idField.getAnnotation(Id::class.java))
    assertNotNull(idField.getAnnotation(UuidGenerator::class.java))
    assertEquals(SqlTypes.VARCHAR, jdbcTypeCode.value)
    assertEquals("id", idField.getAnnotation(Column::class.java).name)
    assertEquals("bucket", bucketField.getAnnotation(Column::class.java).name)
    assertEquals(255, bucketField.getAnnotation(Column::class.java).length)
    assertEquals("service_name", serviceNameField.getAnnotation(Column::class.java).name)
    assertEquals(255, serviceNameField.getAnnotation(Column::class.java).length)
    assertEquals("storage_service", storageServiceField.getAnnotation(Column::class.java).name)
    assertEquals(255, storageServiceField.getAnnotation(Column::class.java).length)
    assertEquals("storage_type", storageTypeField.getAnnotation(Column::class.java).name)
    assertEquals(255, storageTypeField.getAnnotation(Column::class.java).length)
    assertEquals("file_name", fileNameField.getAnnotation(Column::class.java).name)
    assertEquals("TEXT", fileNameField.getAnnotation(Column::class.java).columnDefinition)
    assertEquals(
        "TEXT",
        thumbnailFileNameField.getAnnotation(Column::class.java).columnDefinition,
    )
    assertEquals("TEXT", urlField.getAnnotation(Column::class.java).columnDefinition)
    assertEquals("thumbnail_url", thumbnailUrlField.getAnnotation(Column::class.java).name)
    assertEquals("TEXT", thumbnailUrlField.getAnnotation(Column::class.java).columnDefinition)
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
  fun `app image api service public methods should remain stable`() {
    assertEquals(
        listOf(
            "deleteImage(String, String, String, String):void",
            "uploadImage(String, String, MultipartFile, double, String):ImageEntity",
            "uploadImage(String, String, MultipartFile, double, String, boolean):ImageEntity",
        ),
        publicSignatures(AppImageApiService::class.java),
    )
  }

  @Test
  fun `app image delete recovery service public methods should remain stable`() {
    assertEquals(
        listOf(
            "recoverDeletePendingImages(int):ImageDeleteRecoveryResult",
        ),
        publicSignatures(AppImageDeleteRecoveryService::class.java),
    )
  }

  @Test
  fun `app storage controller upload response type should use image response dto`() {
    val uploadImageMethod =
        AppStorageController::class.declaredFunctions.first { it.name == "uploadImage" }
    val responseType = uploadImageMethod.returnType

    assertEquals(BaseResponse::class, responseType.classifier)
    assertEquals(ImageResponse::class, responseType.arguments.single().type?.classifier)
  }

  @Test
  fun `base response with image response should serialize with stable boot json contract`() {
    contextRunner.run { context ->
      val objectMapper = context.getBean(ObjectMapper::class.java)
      val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111")
      val response =
          BaseResponse.ok(
              ImageResponse(
                  id = imageId,
                  bucket = "image-bucket",
                  serviceName = "gallery",
                  storageService = "cdn",
                  status = "ACTIVE",
                  uploaderId = "member-1",
                  storageType = "R2",
                  fileName = "image-bucket/original.webp",
                  thumbnailFileName = "image-bucket/thumb.webp",
                  url = "https://cdn.example.com/original.webp",
                  thumbnailUrl = "https://cdn.example.com/thumb.webp",
                  width = 640,
                  height = 480,
                  fileSize = 12345,
                  thumbnailWidth = 320,
                  thumbnailHeight = 240,
                  thumbnailFileSize = 4567,
                  createdAt = LocalDateTime.of(2024, 1, 2, 3, 4, 5),
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
