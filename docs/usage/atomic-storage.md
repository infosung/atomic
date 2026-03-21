# atomic.storage Guide

## Why Use This Module

Use `atomic.storage` when your service needs:

- S3-compatible object storage upload/delete
- image upload pipeline with format validation
- optional thumbnail generation (WebP)
- upload result payload including URL, object key, and metadata

This module does not provide Spring Boot auto-configuration.
Register only the beans you use.

## What To Use First

For most services, start with `ImageService`.

- `ImageService.uploadImage(...)`: upload original image and, by default, attempt thumbnail generation
- `ImageService.deleteImage(...)`: delete original and thumbnail by key
- `ImageService` remains the compatibility-stable public facade for this module; the upload/delete orchestration behind it is internal and not a supported host override seam in this line

Low-level `StorageClient.putObject(...)` request models have internal constructors and are intended for module-internal use.

## Prerequisites

- Project tested baseline: Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.3`
- `atomic.storage` itself is Spring-agnostic (you can use it without Spring Boot)
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
- for each `storageType`, `StorageProfile.bucket` must match the bucket configured in its `StorageClient`.
- `StorageProfile.cdn` is used to build returned `url` and `thumbnailUrl`.

## Credentials Patterns

`S3ClientFactory.create(...)` supports two credential paths:

1. Default provider chain
   - use environment/instance profile/task role
   - leave `accessKeyId` and `secretAccessKey` unset
2. Static credentials
   - set `accessKeyId` and `secretAccessKey` together
   - optional `sessionToken` for temporary credentials

```kotlin
val defaultCredentialClient =
    S3ClientFactory.create(
        S3ClientSettings(
            region = "ap-northeast-2",
            endpoint = "https://s3.ap-northeast-2.amazonaws.com",
        ),
    )

val staticCredentialClient =
    S3ClientFactory.create(
        S3ClientSettings(
            region = "ap-northeast-2",
            endpoint = "https://s3.ap-northeast-2.amazonaws.com",
            accessKeyId = "AKIA...",
            secretAccessKey = "...",
            // sessionToken = "...", // optional for temporary credentials
        ),
    )
```

## Input Requirements

- `originFilename` must include an extension.
- supported extensions by default: `jpg`, `jpeg`, `png`, `webp`, `gif`, `bmp` (case-insensitive).
- extension and actual file format must match.
- `quality` must be in range `0.1..1.0`.
- `quality` is a requested scale. Actual thumbnail scale can be reduced by internal caps (`maxOutputPixels`, `maxOutputEdge`).

## Internal Safety Budgets

- generated original object keys are bounded to `512` characters.
- the default object key generator truncates the sanitized filename portion to stay within that budget.
- the final public URL budget is `2048` characters; if the original object key or the final public URL would exceed its built-in budget, upload fails before the original storage write starts.
- thumbnail object key / thumbnail URL budget violations do not fail the original upload; they are reported as `thumbnailUploadFailed=true` with a bounded `thumbnailFailureReason` whose current maximum length is `160`.
- logs summarize long filename/object key values and log their lengths separately instead of emitting the full raw payload.
- these budgets apply to the `ImageService` upload path; direct low-level `StorageClient` usage still remains caller-owned.
- these budgets do not replace multipart/body-size limits or temp-disk policies; application-level upload limits are still mandatory.

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
          generateThumbnail = true,
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
          generateThumbnail = false,
      )
  println(result.url)
}
```

When `generateThumbnail=false`:

- original upload still succeeds normally
- `thumbnailUrl` / `thumbnailFileName` / thumbnail size fields are `null`
- `thumbnailUploadFailed` remains `false`

Runtime rule:

- stream is consumed but not closed by this module.
- upload size limiting is expected at application layer (for example Spring multipart limits).
- upload processing uses local temp files under `java.io.tmpdir`.

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

The list below is not exhaustive. It summarizes common integration-time failure cases.

- `IllegalArgumentException`
  - unknown `storageType`
  - unknown `storageType` profile
  - invalid source file (missing path or not a regular file)
  - invalid `quality` (`0.1..1.0` only)
  - missing filename extension
  - unsupported file extension
  - extension/content mismatch
  - invalid S3 client settings (blank region, partial credentials, malformed endpoint)
- interruption-related exceptions
  - interruption-like errors during thumbnail stage are rethrown and interrupt status is restored
  - original upload failures are propagated as thrown by the underlying storage client
- thumbnail step failures
  - original upload success means request succeeds overall
  - thumbnail generation/upload is attempted only when `generateThumbnail=true`
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
- Thumbnail generation is enabled by default; callers can skip it with `generateThumbnail=false`.
- Thumbnail failure does not roll back original upload.
- Original object key / URL budget failures stop the upload before the original storage write.
- Thumbnail key / URL budget failures are treated as thumbnail-only failures and keep the original upload successful.
- Upload path uses temp files (`source copy`, `raster`, `thumbnail`) in `java.io.tmpdir`.
- Ensure temp directory is writable and sized for peak concurrent uploads.
- Apply upload size/time/concurrency policies in your application layer (for example Spring multipart settings).
