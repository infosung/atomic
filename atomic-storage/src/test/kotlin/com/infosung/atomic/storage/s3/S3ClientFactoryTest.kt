package com.infosung.atomic.storage.s3

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class S3ClientFactoryTest {
  @Test
  fun `create should require non blank region`() {
    assertFailsWith<IllegalArgumentException> {
      S3ClientFactory.create(
          S3ClientSettings(
              region = " ",
          ),
      )
    }
  }

  @Test
  fun `create should build client with static credentials and custom endpoint`() {
    val client =
        S3ClientFactory.create(
            S3ClientSettings(
                region = "auto",
                endpoint = "http://localhost:9000",
                pathStyleAccessEnabled = true,
                accessKeyId = "access",
                secretAccessKey = "secret",
            ),
        )
    try {
      assertNotNull(client)
    } finally {
      client.close()
    }
  }

  @Test
  fun `create should build client with session token`() {
    val client =
        S3ClientFactory.create(
            S3ClientSettings(
                region = "ap-northeast-2",
                accessKeyId = "access",
                secretAccessKey = "secret",
                sessionToken = "token",
            ),
        )
    try {
      assertNotNull(client)
    } finally {
      client.close()
    }
  }

  @Test
  fun `create should reject malformed endpoint`() {
    val error =
        assertFailsWith<IllegalArgumentException> {
          S3ClientFactory.create(
              S3ClientSettings(
                  region = "ap-northeast-2",
                  endpoint = "ht!tp:// bad-endpoint",
              ),
          )
        }
    assertTrue(error.message?.isNotBlank() == true)
  }

  @Test
  fun `create should build client with default credentials provider`() {
    val client =
        S3ClientFactory.create(
            S3ClientSettings(
                region = "ap-northeast-2",
            ),
        )
    try {
      assertNotNull(client)
    } finally {
      client.close()
    }
  }

  @Test
  fun `create should fail when only one static credential field is provided`() {
    assertFailsWith<IllegalArgumentException> {
      S3ClientFactory.create(
          S3ClientSettings(
              region = "ap-northeast-2",
              accessKeyId = "access-only",
          ),
      )
    }
  }

  @Test
  fun `create should fail when session token is provided without static credentials`() {
    assertFailsWith<IllegalArgumentException> {
      S3ClientFactory.create(
          S3ClientSettings(
              region = "ap-northeast-2",
              sessionToken = "token",
          ),
      )
    }
  }
}
