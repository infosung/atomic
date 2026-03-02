package com.infosung.atomic.storage.s3

import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.StorageClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest as AwsPutObjectRequest

/**
 * [StorageClient] implementation backed by AWS SDK [S3Client].
 *
 * Works with AWS S3 and S3-compatible providers (depending on configured client endpoint).
 */
class S3CompatibleStorageClient(
    private val s3Client: S3Client,
    private val bucket: String,
) : StorageClient {
  /**
   * Uploads an object file into configured [bucket].
   *
   * @throws IllegalArgumentException If bucket/objectKey/file/contentLength is invalid.
   */
  override fun putObject(request: PutObjectRequest) {
    require(bucket.isNotBlank()) { "bucket must not be blank." }
    require(request.objectKey.isNotBlank()) { "objectKey must not be blank." }
    require(request.file.exists() && request.file.isFile) { "file must exist and be a file." }
    val contentLength = request.contentLength ?: request.file.length()
    require(contentLength >= 0) { "contentLength must be >= 0." }

    val awsRequestBuilder = AwsPutObjectRequest.builder().bucket(bucket).key(request.objectKey)
    request.contentType?.takeIf { it.isNotBlank() }?.let(awsRequestBuilder::contentType)
    awsRequestBuilder.contentLength(contentLength)
    if (request.metadata.isNotEmpty()) {
      awsRequestBuilder.metadata(request.metadata)
    }

    s3Client.putObject(awsRequestBuilder.build(), request.file.toPath())
  }

  /**
   * Deletes object by key from configured [bucket].
   *
   * Blank keys are ignored as no-op.
   *
   * @throws IllegalArgumentException If bucket is blank.
   */
  override fun deleteObject(objectKey: String) {
    require(bucket.isNotBlank()) { "bucket must not be blank." }
    if (objectKey.isBlank()) return
    val request = DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build()
    s3Client.deleteObject(request)
  }
}
