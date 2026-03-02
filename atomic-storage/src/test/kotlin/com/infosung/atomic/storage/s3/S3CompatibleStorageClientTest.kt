package com.infosung.atomic.storage.s3

import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.PutObjectStreamRequest
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest as AwsPutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

class S3CompatibleStorageClientTest {
  @Test
  fun `putObject should map request into aws putObject request`() {
    val calls = CapturedCalls()
    val s3Client = captureS3Client(calls)
    val client =
        S3CompatibleStorageClient(
            s3Client = s3Client,
            bucket = "my-bucket",
        )
    val file = File.createTempFile("atomic-storage-s3-", ".txt")
    file.writeText("hello")
    val fileSize = file.length()

    try {
      client.putObject(
          PutObjectRequest(
              objectKey = "images/a.png",
              file = file,
              contentType = "image/png",
              metadata = mapOf("width" to "100"),
          ),
      )
    } finally {
      file.delete()
    }

    val request = assertNotNull(calls.putRequest)
    assertEquals("my-bucket", request.bucket())
    assertEquals("images/a.png", request.key())
    assertEquals("image/png", request.contentType())
    assertEquals(fileSize, request.contentLength())
    assertEquals("100", request.metadata()["width"])
    assertEquals(file.toPath(), calls.putPath)
  }

  @Test
  fun `deleteObject should map request into aws deleteObject request`() {
    val calls = CapturedCalls()
    val s3Client = captureS3Client(calls)
    val client =
        S3CompatibleStorageClient(
            s3Client = s3Client,
            bucket = "my-bucket",
        )

    client.deleteObject("images/a.png")

    val request = assertNotNull(calls.deleteRequest)
    assertEquals("my-bucket", request.bucket())
    assertEquals("images/a.png", request.key())
  }

  @Test
  fun `putObject should fail when bucket is blank`() {
    val calls = CapturedCalls()
    val client =
        S3CompatibleStorageClient(
            s3Client = captureS3Client(calls),
            bucket = "",
        )
    val file = File.createTempFile("atomic-storage-s3-", ".txt")
    file.writeText("hello")

    try {
      assertFailsWith<IllegalArgumentException> {
        client.putObject(
            PutObjectRequest(
                objectKey = "images/a.png",
                file = file,
            ),
        )
      }
      assertNull(calls.putRequest)
    } finally {
      file.delete()
    }
  }

  @Test
  fun `putObject should fail when object key is blank`() {
    val calls = CapturedCalls()
    val client =
        S3CompatibleStorageClient(
            s3Client = captureS3Client(calls),
            bucket = "my-bucket",
        )
    val file = File.createTempFile("atomic-storage-s3-", ".txt")
    file.writeText("hello")

    try {
      assertFailsWith<IllegalArgumentException> {
        client.putObject(
            PutObjectRequest(
                objectKey = " ",
                file = file,
            ),
        )
      }
      assertNull(calls.putRequest)
    } finally {
      file.delete()
    }
  }

  @Test
  fun `putObject should fail when file does not exist`() {
    val calls = CapturedCalls()
    val client =
        S3CompatibleStorageClient(
            s3Client = captureS3Client(calls),
            bucket = "my-bucket",
        )
    val missingFile =
        File(
            File(System.getProperty("java.io.tmpdir")),
            "atomic-storage-missing-${System.nanoTime()}.tmp",
        )
    assertFailsWith<IllegalArgumentException> {
      client.putObject(
          PutObjectRequest(
              objectKey = "images/a.png",
              file = missingFile,
          ),
      )
    }
    assertNull(calls.putRequest)
  }

  @Test
  fun `deleteObject should ignore blank key`() {
    val calls = CapturedCalls()
    val client =
        S3CompatibleStorageClient(
            s3Client = captureS3Client(calls),
            bucket = "my-bucket",
        )

    client.deleteObject(" ")

    assertNull(calls.deleteRequest)
  }

  @Test
  fun `putObject should omit empty optional fields`() {
    val calls = CapturedCalls()
    val client =
        S3CompatibleStorageClient(
            s3Client = captureS3Client(calls),
            bucket = "my-bucket",
        )
    val file = File.createTempFile("atomic-storage-s3-", ".txt")
    file.writeText("hello")
    val fileSize = file.length()

    try {
      client.putObject(
          PutObjectRequest(
              objectKey = "images/no-optional.txt",
              file = file,
              contentType = " ",
              metadata = emptyMap(),
          ),
      )
    } finally {
      file.delete()
    }

    val request = assertNotNull(calls.putRequest)
    assertNull(request.contentType())
    assertEquals(fileSize, request.contentLength())
    assertTrue(request.metadata().isEmpty())
  }

  @Test
  fun `putObject stream request should upload via interface adapter`() {
    val calls = CapturedCalls()
    val client =
        S3CompatibleStorageClient(
            s3Client = captureS3Client(calls),
            bucket = "my-bucket",
        )

    client.putObject(
        PutObjectStreamRequest(
            objectKey = "images/from-stream.png",
            inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            contentType = "image/png",
            metadata = mapOf("size" to "3"),
            contentLength = 3,
        ),
    )

    val request = assertNotNull(calls.putRequest)
    assertEquals("my-bucket", request.bucket())
    assertEquals("images/from-stream.png", request.key())
    assertEquals("image/png", request.contentType())
    assertEquals(3L, request.contentLength())
    assertEquals("3", request.metadata()["content-length"])
    assertEquals("3", request.metadata()["size"])
  }

  @Test
  fun `putObject should fail when file is directory`() {
    val calls = CapturedCalls()
    val client =
        S3CompatibleStorageClient(
            s3Client = captureS3Client(calls),
            bucket = "my-bucket",
        )
    val directory = Files.createTempDirectory("atomic-storage-s3-dir-").toFile()

    try {
      assertFailsWith<IllegalArgumentException> {
        client.putObject(
            PutObjectRequest(
                objectKey = "images/a.png",
                file = directory,
            ),
        )
      }
      assertNull(calls.putRequest)
    } finally {
      directory.delete()
    }
  }

  @Test
  fun `deleteObject should fail when bucket is blank`() {
    val calls = CapturedCalls()
    val client =
        S3CompatibleStorageClient(
            s3Client = captureS3Client(calls),
            bucket = "",
        )

    assertFailsWith<IllegalArgumentException> { client.deleteObject("images/a.png") }
    assertNull(calls.deleteRequest)
  }

  private data class CapturedCalls(
      var putRequest: AwsPutObjectRequest? = null,
      var putPath: Path? = null,
      var deleteRequest: DeleteObjectRequest? = null,
  )

  private fun captureS3Client(calls: CapturedCalls): S3Client {
    val handler =
        java.lang.reflect.InvocationHandler { _, method, args ->
          when (method.name) {
            "putObject" -> {
              calls.putRequest = args?.get(0) as AwsPutObjectRequest
              calls.putPath = args.get(1) as Path
              PutObjectResponse.builder().build()
            }

            "deleteObject" -> {
              calls.deleteRequest = args?.get(0) as DeleteObjectRequest
              DeleteObjectResponse.builder().build()
            }

            "close" -> Unit
            "serviceName" -> "S3"
            "toString" -> "S3ClientProxy"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> false
            else -> null
          }
        }
    return Proxy.newProxyInstance(
        S3Client::class.java.classLoader,
        arrayOf(S3Client::class.java),
        handler,
    ) as S3Client
  }
}
