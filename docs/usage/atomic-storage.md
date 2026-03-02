# atomic.storage Guide

## Why Use This Module

Use `atomic.storage` when your service needs:

- S3-compatible object storage upload/delete
- image upload pipeline with format validation
- automatic thumbnail generation (WebP)
- upload result payload including URL, object key, and metadata

This module does not provide Spring Boot auto-configuration.
Register only the beans you use.

## What To Use First

For most services, start with `ImageService`.

- `ImageService.uploadImage(...)`: upload original image + optional thumbnail
- `ImageService.deleteImage(...)`: delete original and thumbnail by key

Low-level `StorageClient.putObject(...)` request models have internal constructors and are intended for module-internal use.

## Prerequisites

- Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.3` baseline
- one S3-compatible backend (AWS S3, Cloudflare R2, MinIO, etc.)
- valid bucket, endpoint/region, and credentials

## Quick Config (S3-Compatible)

```kotlin
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.ImageService
import com.infosung.atomic.storage.s3.S3ClientFactory
import com.infosung.atomic.storage.s3.S3ClientSettings
import com.infosung.atomic.storage.s3.S3CompatibleStorageClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StorageConfig {
  @Bean
  fun storageClients(): Map<String, StorageClient> {
    val s3Client =
        S3ClientFactory.create(
            S3ClientSettings(
                region = "ap-northeast-2",
                endpoint = "https://s3.ap-northeast-2.amazonaws.com",
                pathStyleAccessEnabled = false,
            ),
        )
    return mapOf(
        "S3" to S3CompatibleStorageClient(s3Client = s3Client, bucket = "my-bucket"),
    )
  }

  @Bean
  fun storageProfiles(): Map<String, StorageProfile> =
      mapOf(
          "S3" to
              StorageProfile(
                  bucket = "my-bucket",
                  cdn = "https://cdn.example.com",
                  prependBucketOnObjectKey = false,
              ),
      )

  @Bean
  fun imageService(
      storageClients: Map<String, StorageClient>,
      storageProfiles: Map<String, StorageProfile>,
  ) = ImageService(
      storageClients = storageClients,
      storageProfiles = storageProfiles,
  )
}
```

Important:

- `storageClients` key and `storageProfiles` key must match (for example both `"S3"`).
- `StorageProfile.cdn` is used to build returned `url` and `thumbnailUrl`.

## Upload Example

```kotlin
import com.infosung.atomic.storage.image.ImageService
import java.io.File

fun uploadExample(imageService: ImageService, file: File) {
  val result =
      imageService.uploadImage(
          file = file,
          originFilename = "profile.jpg",
          storageType = "S3",
          quality = 0.8,
      )

  println(result.url)
  println(result.thumbnailUrl)
  println(result.fileName)
  println(result.thumbnailFileName)
}
```

## Stream Upload Example

```kotlin
import com.infosung.atomic.storage.image.ImageService
import java.io.InputStream

fun uploadStreamExample(imageService: ImageService, inputStream: InputStream) {
  val result =
      imageService.uploadImage(
          inputStream = inputStream,
          originFilename = "banner.png",
          storageType = "S3",
          quality = 1.0,
      )
  println(result.url)
}
```

Runtime rule:

- stream is consumed but not closed by this module.
- upload size limiting is expected at application layer (for example Spring multipart limits).

## Delete Example

```kotlin
import com.infosung.atomic.storage.image.ImageService

fun deleteExample(
    imageService: ImageService,
    fileName: String?,
    thumbnailFileName: String?,
) {
  imageService.deleteImage(
      storageType = "S3",
      fileName = fileName,
      thumbnailFileName = thumbnailFileName,
  )
}
```

## Exception and Failure Semantics

- `IllegalArgumentException`
  - unknown `storageType`
  - invalid `quality` (`0.1..1.0` only)
  - unsupported file extension
  - extension/content mismatch
  - invalid S3 client settings (blank region, partial credentials, malformed endpoint)
- interruption-related exceptions
  - upload operation rethrows interruption-like errors and restores thread interrupt flag
- thumbnail step failures
  - original upload success is kept
  - failure is reported via `thumbnailUploadFailed=true` and `thumbnailFailureReason`

## Returned Key and URL Behavior

- `storageObjectKey` / `storageThumbnailObjectKey`
  - object key without bucket prefix
- `fileName` / `thumbnailFileName`
  - may include `bucket/` prefix when `prependBucketOnObjectKey=true`
- `url` / `thumbnailUrl`
  - composed as `{cdn}/{fileName}`

## Operational Notes

- This module stores original width/height/size metadata on upload.
- Thumbnail output format is WebP (`*_thumb.webp`).
- Apply upload size/time/concurrency policies in your Spring service.
