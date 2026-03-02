package com.infosung.atomic.storage.s3

import java.net.URI
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration

/**
 * Settings used to create S3-compatible clients.
 *
 * @property region AWS region or provider-specific region value.
 * @property endpoint Optional custom endpoint (for example MinIO/R2).
 * @property pathStyleAccessEnabled Enables path-style access when provider requires it.
 * @property accessKeyId Optional static credential access key.
 * @property secretAccessKey Optional static credential secret key.
 * @property sessionToken Optional session token for temporary credentials.
 */
data class S3ClientSettings(
    val region: String,
    val endpoint: String? = null,
    val pathStyleAccessEnabled: Boolean = false,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val sessionToken: String? = null,
)

/**
 * Factory for AWS SDK [S3Client] instances used by this module.
 */
object S3ClientFactory {
  /**
   * Creates an [S3Client] from [settings].
   *
   * Credential behavior:
   * - If both `accessKeyId` and `secretAccessKey` are set, static credentials are used.
   * - If neither is set, default credential provider chain is used.
   * - Partial static credential input is rejected to avoid accidental fallback.
   *
   * @throws IllegalArgumentException
   *   - if region is blank
   *   - if only one of `accessKeyId`/`secretAccessKey` is provided
   *   - if `sessionToken` is provided without static credentials
   *   - if endpoint is malformed
   */
  fun create(settings: S3ClientSettings): S3Client {
    require(settings.region.isNotBlank()) { "region must not be blank." }
    val accessKeyId = settings.accessKeyId?.takeIf { it.isNotBlank() }
    val secretAccessKey = settings.secretAccessKey?.takeIf { it.isNotBlank() }
    val hasPartialStaticCredential = (accessKeyId == null) != (secretAccessKey == null)
    require(!hasPartialStaticCredential) {
      "accessKeyId and secretAccessKey must be provided together."
    }
    require(settings.sessionToken.isNullOrBlank() || accessKeyId != null) {
      "sessionToken requires both accessKeyId and secretAccessKey."
    }

    val builder =
        S3Client.builder()
            .region(Region.of(settings.region))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(settings.pathStyleAccessEnabled)
                    .build(),
            )

    if (!settings.endpoint.isNullOrBlank()) {
      builder.endpointOverride(URI.create(settings.endpoint))
    }

    val hasStaticCredentials = accessKeyId != null && secretAccessKey != null
    if (hasStaticCredentials) {
      val resolvedAccessKeyId = requireNotNull(accessKeyId)
      val resolvedSecretAccessKey = requireNotNull(secretAccessKey)
      val credentialsProvider =
          if (!settings.sessionToken.isNullOrBlank()) {
            StaticCredentialsProvider.create(
                AwsSessionCredentials.create(
                    resolvedAccessKeyId,
                    resolvedSecretAccessKey,
                    settings.sessionToken,
                ),
            )
          } else {
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(resolvedAccessKeyId, resolvedSecretAccessKey),
            )
          }
      builder.credentialsProvider(credentialsProvider)
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.builder().build())
    }
    return builder.build()
  }
}
