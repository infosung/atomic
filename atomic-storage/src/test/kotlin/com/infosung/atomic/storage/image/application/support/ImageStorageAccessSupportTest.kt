package com.infosung.atomic.storage.image.application.support

import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ImageStorageAccessSupportTest {
  @Test
  fun `resolve should return paired client and profile`() {
    val client = NoopStorageClient()
    val support =
        ImageStorageAccessSupport(
            storageClients = mapOf("S3" to client),
            storageProfiles =
                mapOf("S3" to StorageProfile(bucket = "bucket", cdn = "https://cdn.example.com")),
        )

    val access = support.resolve("S3")

    assertSame(client, access.storageClient)
    assertEquals("bucket", access.storageProfile.bucket)
  }

  @Test
  fun `resolve should fail when storage client is missing`() {
    val support =
        ImageStorageAccessSupport(
            storageClients = emptyMap(),
            storageProfiles =
                mapOf("S3" to StorageProfile(bucket = "bucket", cdn = "https://cdn.example.com")),
        )

    val exception = assertFailsWith<IllegalArgumentException> { support.resolve("S3") }

    assertEquals("Unknown storageType: S3", exception.message)
  }

  @Test
  fun `resolve should fail when storage profile is missing`() {
    val support =
        ImageStorageAccessSupport(
            storageClients = mapOf("S3" to NoopStorageClient()),
            storageProfiles = emptyMap(),
        )

    val exception = assertFailsWith<IllegalArgumentException> { support.resolve("S3") }

    assertEquals("Unknown storageType profile: S3", exception.message)
  }

  @Test
  fun `stored object key and delete key normalization should respect bucket prefix policy`() {
    val support =
        ImageStorageAccessSupport(
            storageClients = mapOf("R2" to NoopStorageClient()),
            storageProfiles =
                mapOf(
                    "R2" to
                        StorageProfile(
                            bucket = "bucket",
                            cdn = "https://cdn.example.com",
                            prependBucketOnObjectKey = true,
                        ),
                ),
        )

    val access = support.resolve("R2")

    assertEquals(
        "bucket/images/test/original.png",
        support.toStoredObjectKey(access, "images/test/original.png"),
    )
    assertEquals(
        "https://cdn.example.com/bucket/images/test/original.png",
        support.toPublicUrl(access, "images/test/original.png"),
    )
    assertEquals(
        "images/test/original.png",
        support.normalizeDeleteObjectKey(access, "bucket/images/test/original.png"),
    )
  }

  @Test
  fun `delete key normalization should keep raw key when bucket prefix is disabled`() {
    val support =
        ImageStorageAccessSupport(
            storageClients = mapOf("S3" to NoopStorageClient()),
            storageProfiles =
                mapOf(
                    "S3" to
                        StorageProfile(
                            bucket = "bucket",
                            cdn = "https://cdn.example.com/",
                            prependBucketOnObjectKey = false,
                        ),
                ),
        )

    val access = support.resolve("S3")

    assertEquals(
        "images/test/original.png", support.toStoredObjectKey(access, "images/test/original.png"))
    assertEquals(
        "https://cdn.example.com/images/test/original.png",
        support.toPublicUrl(access, "images/test/original.png"),
    )
    assertEquals(
        "images/test/original.png",
        support.normalizeDeleteObjectKey(access, "images/test/original.png"),
    )
  }

  private class NoopStorageClient : StorageClient {
    override fun putObject(request: PutObjectRequest) = Unit

    override fun deleteObject(objectKey: String) = Unit
  }
}
