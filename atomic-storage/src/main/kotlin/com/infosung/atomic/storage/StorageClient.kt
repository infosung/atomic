package com.infosung.atomic.storage

import java.io.File
import java.io.InputStream
import java.util.logging.Logger

/**
 * File-based upload request for [StorageClient.putObject].
 *
 * @property objectKey Storage key relative to the bucket/container.
 * @property file Local file to upload.
 * @property contentType Optional MIME type. Blank values are ignored by concrete clients.
 * @property metadata Optional user metadata sent together with the object.
 * @property contentLength Optional content length for storage clients that require explicit length.
 */
class PutObjectRequest internal constructor(
    val objectKey: String,
    val file: File,
    val contentType: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val contentLength: Long? = null,
)

/**
 * Stream-based upload request for [StorageClient.putObject].
 *
 * @property objectKey Storage key relative to the bucket/container.
 * @property inputStream Stream to upload. The stream is consumed but not closed by this module.
 * @property contentType Optional MIME type. Blank values are ignored by concrete clients.
 * @property metadata Optional user metadata sent together with the object.
 * @property contentLength Optional expected stream length. If set, it must match actual copied size.
 */
class PutObjectStreamRequest internal constructor(
    val objectKey: String,
    val inputStream: InputStream,
    val contentType: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val contentLength: Long? = null,
)

/**
 * Contract for object storage operations used by this module.
 */
interface StorageClient {
  /**
   * Uploads a local file to object storage.
   *
   * @throws IllegalArgumentException If request values are invalid for the concrete implementation.
   */
  fun putObject(request: PutObjectRequest)

  /**
   * Uploads a stream to object storage through a temporary file adapter.
   *
   * Behavior:
   * - The stream is consumed but not closed.
   * - Actual byte length is recorded as [CONTENT_LENGTH_METADATA_KEY] metadata.
   * - If [PutObjectStreamRequest.contentLength] is set, a length mismatch fails fast.
   *
   * @throws IllegalArgumentException If expected length does not match actual copied size.
   */
  fun putObject(request: PutObjectStreamRequest) {
    val tempFile = File.createTempFile("atomic-storage-stream-", ".tmp")
    try {
      // Size limiting is handled by the application layer (for example, Spring multipart limits).
      tempFile.outputStream().use { output -> request.inputStream.copyTo(output) }
      val actualContentLength = tempFile.length()
      request.contentLength?.let { expected ->
        require(expected == actualContentLength) {
          "Input stream length mismatch: expected=$expected, actual=$actualContentLength"
        }
      }
      putObject(
          PutObjectRequest(
              objectKey = request.objectKey,
              file = tempFile,
              contentType = request.contentType,
              metadata =
                  request.metadata +
                      mapOf(CONTENT_LENGTH_METADATA_KEY to actualContentLength.toString()),
              contentLength = actualContentLength,
          ),
      )
    } finally {
      deleteTempFile(tempFile, "stream adapter")
    }
  }

  /**
   * Deletes an object by key.
   *
   * @param objectKey Storage key relative to the bucket/container.
   */
  fun deleteObject(objectKey: String)

  private fun deleteTempFile(
      tempFile: File,
      context: String,
  ) {
    if (!tempFile.exists()) return
    if (!tempFile.delete()) {
      tempFile.deleteOnExit()
      logger.warning(
          "Failed to delete temporary file ($context): ${tempFile.absolutePath}. Scheduled deleteOnExit().",
      )
    }
  }

  companion object {
    const val CONTENT_LENGTH_METADATA_KEY: String = "content-length"
    private val logger: Logger = Logger.getLogger(StorageClient::class.java.name)
  }
}
