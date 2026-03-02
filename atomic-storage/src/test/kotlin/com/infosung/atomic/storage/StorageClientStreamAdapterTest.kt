package com.infosung.atomic.storage

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StorageClientStreamAdapterTest {
  @Test
  fun `putObject stream adapter should delete temp file when delegate fails`() {
    val client = ThrowingStorageClient()

    assertFailsWith<IllegalStateException> {
      client.putObject(
          PutObjectStreamRequest(
              objectKey = "images/fail.bin",
              inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
          ),
      )
    }

    val capturedPath = assertNotNull(client.capturedFilePath)
    assertFalse(File(capturedPath).exists())
  }

  @Test
  fun `putObject stream adapter should pass actual content length as metadata and request field`() {
    val client = CapturingStorageClient()

    client.putObject(
        PutObjectStreamRequest(
            objectKey = "images/success.bin",
            inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            metadata = mapOf("type" to "binary"),
            contentLength = 4,
        ),
    )

    val captured = assertNotNull(client.capturedRequest)
    assertEquals(4L, captured.contentLength)
    assertEquals("4", captured.metadata[StorageClient.CONTENT_LENGTH_METADATA_KEY])
    assertEquals("binary", captured.metadata["type"])
  }

  @Test
  fun `putObject stream adapter should fail when provided content length mismatches`() {
    val client = CapturingStorageClient()

    assertFailsWith<IllegalArgumentException> {
      client.putObject(
          PutObjectStreamRequest(
              objectKey = "images/mismatch.bin",
              inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
              contentLength = 10,
          ),
      )
    }

    assertTrue(client.capturedRequest == null)
  }

  @Test
  fun `putObject stream adapter should not close caller owned input stream`() {
    val client = CapturingStorageClient()
    val inputStream = NonClosingInputStream(byteArrayOf(1, 2, 3))

    client.putObject(
        PutObjectStreamRequest(
            objectKey = "images/non-close.bin",
            inputStream = inputStream,
        ),
    )

    assertFalse(inputStream.closed)
  }

  private class ThrowingStorageClient : StorageClient {
    var capturedFilePath: String? = null

    override fun putObject(request: PutObjectRequest) {
      capturedFilePath = request.file.absolutePath
      throw IllegalStateException("put failed")
    }

    override fun deleteObject(objectKey: String) = Unit
  }

  private class CapturingStorageClient : StorageClient {
    var capturedRequest: PutObjectRequest? = null

    override fun putObject(request: PutObjectRequest) {
      capturedRequest = request
    }

    override fun deleteObject(objectKey: String) = Unit
  }

  private class NonClosingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var closed: Boolean = false

    override fun close() {
      closed = true
    }
  }
}
