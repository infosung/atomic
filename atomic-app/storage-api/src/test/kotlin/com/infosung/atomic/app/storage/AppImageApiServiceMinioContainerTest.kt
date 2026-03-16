package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.GeneratedThumbnail
import com.infosung.atomic.storage.image.ImageInputValidator
import com.infosung.atomic.storage.image.ImageMetadata
import com.infosung.atomic.storage.image.ImageMetadataReader
import com.infosung.atomic.storage.image.ImageObjectKeyGenerator
import com.infosung.atomic.storage.image.ImageService
import com.infosung.atomic.storage.image.ImageThumbnailGenerator
import com.infosung.atomic.storage.image.ValidatedImageInput
import com.infosung.atomic.storage.s3.S3ClientFactory
import com.infosung.atomic.storage.s3.S3ClientSettings
import com.infosung.atomic.storage.s3.S3CompatibleStorageClient
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AppImageEntityTxService::class)
@Testcontainers(disabledWithoutDocker = true)
class AppImageApiServiceMinioContainerTest {
  @Autowired private lateinit var imageEntityTxService: AppImageEntityTxService

  @Test
  fun `upload and delete should work with real minio container`() {
    val endpoint = minioEndpoint()
    val bucket = "atomic-storage-test"
    val storageType = "S3"
    val s3Client =
        S3ClientFactory.create(
            S3ClientSettings(
                region = "us-east-1",
                endpoint = endpoint,
                pathStyleAccessEnabled = true,
                accessKeyId = MINIO_ACCESS_KEY,
                secretAccessKey = MINIO_SECRET_KEY,
            ),
        )
    ensureBucketExists(s3Client = s3Client, bucket = bucket)
    val storageClient = S3CompatibleStorageClient(s3Client = s3Client, bucket = bucket)

    val imageService =
        createImageService(
            storageType = storageType,
            bucket = bucket,
            cdn = endpoint,
            storageClient = storageClient,
        )
    val appImageApiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf(storageType to storageClient),
            properties = AtomicAppImageProperties(),
        )

    try {
      val multipartFile =
          MockMultipartFile(
              "file",
              "integration.png",
              "image/png",
              "integration-image".toByteArray(),
          )
      val uploaded =
          appImageApiService.uploadImage(
              serviceName = "svc",
              storageService = "S3",
              multipartFile = multipartFile,
              quality = 1.0,
          )
      val imageId = requireNotNull(uploaded.id)
      val originalObjectKey = requireNotNull(uploaded.fileName)
      val thumbnailObjectKey = requireNotNull(uploaded.thumbnailFileName)
      assertNotNull(imageId)
      assertTrue(objectExists(s3Client = s3Client, bucket = bucket, objectKey = originalObjectKey))
      assertTrue(objectExists(s3Client = s3Client, bucket = bucket, objectKey = thumbnailObjectKey))

      appImageApiService.deleteImage(
          serviceName = "svc",
          storageService = "S3",
          imageId = imageId.toString(),
      )

      assertFalse(objectExists(s3Client = s3Client, bucket = bucket, objectKey = originalObjectKey))
      assertFalse(
          objectExists(
              s3Client = s3Client,
              bucket = bucket,
              objectKey = thumbnailObjectKey,
          ),
      )
      runCatching { imageEntityTxService.findByIdOrThrow(imageId, imageId.toString()) }
          .onSuccess { error("image metadata should be deleted") }
    } finally {
      s3Client.close()
    }
  }

  private fun createImageService(
      storageType: String,
      bucket: String,
      cdn: String,
      storageClient: S3CompatibleStorageClient,
  ): ImageService {
    return ImageService(
        storageClients = mapOf(storageType to storageClient),
        storageProfiles =
            mapOf(
                storageType to
                    StorageProfile(
                        bucket = bucket,
                        cdn = cdn,
                        prependBucketOnObjectKey = false,
                    ),
            ),
        objectKeyGenerator =
            ImageObjectKeyGenerator { "images/${UUID.randomUUID()}_${it.substringAfterLast("/")}" },
        imageInputValidator =
            ImageInputValidator { _, _ ->
              ValidatedImageInput(
                  extension = "png",
                  contentType = "image/png",
                  detectedFormat = "PNG",
              )
            },
        metadataReader =
            ImageMetadataReader { file ->
              ImageMetadata(
                  width = 120,
                  height = 80,
                  size = file.length(),
              )
            },
        thumbnailGenerator =
            ImageThumbnailGenerator { _, _, sourceObjectKey, _ ->
              val thumbnailFile = File.createTempFile("atomic-minio-thumb-", ".webp")
              thumbnailFile.writeBytes(byteArrayOf(7, 7, 7, 7))
              GeneratedThumbnail(
                  objectKey = "${sourceObjectKey.substringBeforeLast(".")}_thumb.webp",
                  file = thumbnailFile,
                  metadata = ImageMetadata(width = 60, height = 40, size = thumbnailFile.length()),
              )
            },
    )
  }

  private fun ensureBucketExists(
      s3Client: S3Client,
      bucket: String,
  ) {
    try {
      s3Client.headBucket { it.bucket(bucket) }
    } catch (_: NoSuchBucketException) {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
    } catch (e: S3Exception) {
      if (e.statusCode() == 404) {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
      } else {
        throw e
      }
    }
  }

  private fun objectExists(
      s3Client: S3Client,
      bucket: String,
      objectKey: String,
  ): Boolean {
    return try {
      s3Client.headObject(
          HeadObjectRequest.builder().bucket(bucket).key(objectKey).build(),
      )
      true
    } catch (_: NoSuchKeyException) {
      false
    } catch (e: S3Exception) {
      if (e.statusCode() == 404) {
        false
      } else {
        throw e
      }
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = [ImageEntity::class])
  @EnableJpaRepositories(basePackageClasses = [ImageRepository::class])
  class TestConfiguration

  companion object {
    private const val MINIO_ACCESS_KEY = "minioadmin"
    private const val MINIO_SECRET_KEY = "minioadmin"

    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("app_image_api_minio_${UUID.randomUUID()}")

    @Container
    @JvmStatic
    private val minio: GenericContainer<*> =
        GenericContainer(DockerImageName.parse("minio/minio:RELEASE.2025-02-28T09-55-16Z"))
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000, 9001)

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
      registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
    }

    private fun minioEndpoint(): String {
      return "http://${minio.host}:${minio.getMappedPort(9000)}"
    }
  }
}
