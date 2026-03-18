package com.infosung.atomic.app.version.application.service

import com.infosung.atomic.app.version.application.exception.InvalidAppVersionException
import com.infosung.atomic.app.version.application.exception.VersionPolicyNotFoundException
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import com.infosung.atomic.app.version.domain.SemanticVersion
import com.infosung.atomic.app.version.domain.VersionCheckDecision
import org.slf4j.LoggerFactory

internal class CheckAppVersionService(
    private val loadVersionPolicyPort: LoadVersionPolicyPort,
    private val defaultStoreUrl: String,
) : CheckAppVersionUseCase {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun check(
      service: String,
      platform: String,
      appVersion: String,
  ): VersionCheckDecision {
    val parsedVersion = parseVersion(appVersion)
    val normalizedService = service.trim().uppercase()
    val normalizedPlatform = platform.trim().uppercase()
    log.debug(
        "Checking app version through use-case: service={}, platform={}, appVersion={}",
        normalizedService,
        normalizedPlatform,
        appVersion,
    )

    val latestRegistered =
        loadVersionPolicyPort.loadLatestRegistered(normalizedService, normalizedPlatform)
            ?: run {
              log.warn(
                  "No version policies found in use-case: service={}, platform={}",
                  normalizedService,
                  normalizedPlatform,
              )
              throw VersionPolicyNotFoundException(
                  service = normalizedService,
                  platform = normalizedPlatform,
              )
            }

    val current =
        loadVersionPolicyPort.loadLatestStoreAvailable(normalizedService, normalizedPlatform)
            ?: latestRegistered.also {
              log.warn(
                  "No store-available version policy found in use-case; falling back to latest registered version: service={}, platform={}, latestRegistered={}",
                  normalizedService,
                  normalizedPlatform,
                  latestRegistered.version,
              )
            }

    log.debug(
        "Using semantic client version directly in use-case without exact persistence lookup: service={}, platform={}, userVersion={}",
        normalizedService,
        normalizedPlatform,
        parsedVersion,
    )

    val requiredTarget =
        loadVersionPolicyPort.loadRequiredUpdateTargetAbove(
            service = normalizedService,
            platform = normalizedPlatform,
            version = parsedVersion,
        )
    if (requiredTarget != null) {
      log.info(
          "Required update detected in use-case: service={}, platform={}, userVersion={}, targetVersion={}",
          normalizedService,
          normalizedPlatform,
          appVersion,
          requiredTarget.version,
      )
    }

    val resolvedStoreUrl = requiredTarget?.storeUrl?.takeIf { it.isNotBlank() } ?: defaultStoreUrl
    log.debug(
        "Resolved app version decision in use-case: service={}, platform={}, currentVersion={}, userVersion={}, requiredUpdate={}, storeUrlSource={}, storeUrlLength={}",
        normalizedService,
        normalizedPlatform,
        current.version,
        parsedVersion,
        requiredTarget != null,
        if (requiredTarget?.storeUrl?.isNotBlank() == true) "policy" else "default",
        resolvedStoreUrl.length,
    )

    return VersionCheckDecision(
        currentVersion = current.version.toString(),
        userVersion = parsedVersion.toString(),
        requiredUpdate = requiredTarget != null,
        storeUrl = resolvedStoreUrl,
    )
  }

  private fun parseVersion(version: String): SemanticVersion {
    val segments = version.trim().split('.')
    if (segments.size != 3) {
      log.warn("Invalid app version format in use-case (semantic required): appVersion={}", version)
      throw InvalidAppVersionException("Version must be semantic format: x.y.z")
    }
    val numbers =
        segments.map {
          it.toIntOrNull()
              ?: throw InvalidAppVersionException("Version segment must be numeric: $version")
                  .also {
                    log.warn(
                        "Invalid app version segment in use-case (numeric required): appVersion={}",
                        version,
                    )
                  }
        }
    if (numbers.any { it < 0 }) {
      log.warn(
          "Invalid app version value in use-case (non-negative required): appVersion={}", version)
      throw InvalidAppVersionException("Version must not contain negative numbers.")
    }
    return SemanticVersion(
        major = numbers[0],
        minor = numbers[1],
        patch = numbers[2],
    )
  }
}
