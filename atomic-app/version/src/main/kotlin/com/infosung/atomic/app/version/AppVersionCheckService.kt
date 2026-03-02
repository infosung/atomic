package com.infosung.atomic.app.version

import com.infosung.atomic.contract.exception.HttpStatusException
import org.slf4j.LoggerFactory

/** Validates client app version against service/platform version policies. */
class AppVersionCheckService(
    private val serviceVersionRepository: ServiceVersionRepository,
    private val defaultStoreUrl: String,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  /**
   * Checks version policy and returns update requirement.
   *
   * @throws HttpStatusException 400 when request format is invalid.
   * @throws HttpStatusException 404 when no version policy exists.
   */
  fun checkVersion(request: VersionCheckRequest): VersionCheckResult {
    val parsedVersion = parseVersion(request.appVersion)
    val service = request.service.trim().uppercase()
    val platform = request.platform.trim().uppercase()
    log.debug(
        "Checking version policy: service={}, platform={}, appVersion={}",
        service,
        platform,
        request.appVersion,
    )

    val versions =
        serviceVersionRepository
            .findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                service,
                platform,
            )
    if (versions.isEmpty()) {
      log.warn("No version policies found: service={}, platform={}", service, platform)
      throw HttpStatusException(
          status = 404,
          message = "No service version policy found for service=$service, platform=$platform",
      )
    }

    val userVersion =
        versions.firstOrNull {
          it.mainVersion == parsedVersion.major &&
              it.minorVersion == parsedVersion.minor &&
              it.patchNumber == parsedVersion.patch
        }
            ?: throw HttpStatusException(
                    status = 400,
                    message = "Version does not match any registered service version policy.",
                )
                .also {
                  log.warn(
                      "Client version is not registered in policy: service={}, platform={}, appVersion={}",
                      service,
                      platform,
                      request.appVersion,
                  )
                }

    val requiredTarget =
        versions.firstOrNull { it.requireUpdate && isHigherVersion(it, parsedVersion) }
    val current = versions.first()

    log.debug(
        "Checked app version: service={}, platform={}, userVersion={}, requiredUpdate={}",
        service,
        platform,
        request.appVersion,
        requiredTarget != null,
    )
    if (requiredTarget != null) {
      log.info(
          "Required update detected: service={}, platform={}, userVersion={}, targetVersion={}.{}.{}",
          service,
          platform,
          request.appVersion,
          requiredTarget.mainVersion,
          requiredTarget.minorVersion,
          requiredTarget.patchNumber,
      )
    }

    return VersionCheckResult(
        currentVersion = toVersionString(current),
        userVersion = toVersionString(userVersion),
        requiredUpdate = requiredTarget != null,
        storeUrl = requiredTarget?.storeUrl?.takeIf { it.isNotBlank() } ?: defaultStoreUrl,
    )
  }

  private fun parseVersion(version: String): SemanticVersion {
    val segments = version.trim().split('.')
    if (segments.size != 3) {
      log.warn("Invalid app version format (semantic required): appVersion={}", version)
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
                        "Invalid app version segment (numeric required): appVersion={}", version)
                  }
        }
    if (numbers.any { it < 0 }) {
      log.warn("Invalid app version value (non-negative required): appVersion={}", version)
      throw HttpStatusException(
          status = 400, message = "Version must not contain negative numbers.")
    }
    return SemanticVersion(major = numbers[0], minor = numbers[1], patch = numbers[2])
  }

  private fun isHigherVersion(
      candidate: ServiceVersionEntity,
      source: SemanticVersion,
  ): Boolean {
    if (candidate.mainVersion != source.major) return candidate.mainVersion > source.major
    if (candidate.minorVersion != source.minor) return candidate.minorVersion > source.minor
    return candidate.patchNumber > source.patch
  }

  private fun toVersionString(version: ServiceVersionEntity): String {
    return "${version.mainVersion}.${version.minorVersion}.${version.patchNumber}"
  }

  private data class SemanticVersion(
      val major: Int,
      val minor: Int,
      val patch: Int,
  )
}
