package com.infosung.atomic.app.version.application.service

import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import com.infosung.atomic.app.version.domain.SemanticVersion
import com.infosung.atomic.app.version.domain.VersionCheckDecision
import com.infosung.atomic.contract.exception.HttpStatusException
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
              throw HttpStatusException(
                  status = 404,
                  message =
                      "No service version policy found for service=$normalizedService, platform=$normalizedPlatform",
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

    val userVersionPolicy =
        loadVersionPolicyPort.loadExact(
            service = normalizedService,
            platform = normalizedPlatform,
            version = parsedVersion,
        )
    if (userVersionPolicy == null) {
      log.info(
          "Client version is not explicitly registered in use-case; continuing semantic evaluation: service={}, platform={}, appVersion={}",
          normalizedService,
          normalizedPlatform,
          appVersion,
      )
    } else {
      log.debug(
          "Loaded exact client version policy in use-case: service={}, platform={}, userVersion={}",
          normalizedService,
          normalizedPlatform,
          userVersionPolicy.version,
      )
    }

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
        userVersionPolicy?.version ?: parsedVersion,
        requiredTarget != null,
        if (requiredTarget?.storeUrl?.isNotBlank() == true) "policy" else "default",
        resolvedStoreUrl.length,
    )

    return VersionCheckDecision(
        currentVersion = current.version.toString(),
        userVersion = (userVersionPolicy?.version ?: parsedVersion).toString(),
        requiredUpdate = requiredTarget != null,
        storeUrl = resolvedStoreUrl,
    )
  }

  private fun parseVersion(version: String): SemanticVersion {
    val segments = version.trim().split('.')
    if (segments.size != 3) {
      log.warn("Invalid app version format in use-case (semantic required): appVersion={}", version)
      throw HttpStatusException(status = 400, message = "Version must be semantic format: x.y.z")
    }
    val numbers =
        segments.map {
          it.toIntOrNull()
              ?: throw HttpStatusException(
                      status = 400,
                      message = "Version segment must be numeric: $version",
                  )
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
      throw HttpStatusException(
          status = 400,
          message = "Version must not contain negative numbers.",
      )
    }
    return SemanticVersion(
        major = numbers[0],
        minor = numbers[1],
        patch = numbers[2],
    )
  }
}
