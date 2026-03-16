package com.infosung.atomic.app.version

import com.infosung.atomic.contract.exception.HttpStatusException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest

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

    log.debug("Loading latest version policy row: service={}, platform={}", service, platform)
    val latestRegistered =
        serviceVersionRepository
            .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                service,
                platform,
            )
    if (latestRegistered == null) {
      log.warn("No version policies found: service={}, platform={}", service, platform)
      throw HttpStatusException(
          status = 404,
          message = "No service version policy found for service=$service, platform=$platform",
      )
    }
    val current =
        serviceVersionRepository
            .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                service,
                platform,
            )
            ?: latestRegistered.also {
              log.warn(
                  "No store-available version policy found; falling back to latest registered version: service={}, platform={}, latestRegistered={}.{}.{}",
                  service,
                  platform,
                  latestRegistered.mainVersion,
                  latestRegistered.minorVersion,
                  latestRegistered.patchNumber,
              )
            }
    log.debug(
        "Loaded latest version policy row: service={}, platform={}, currentVersion={}.{}.{}",
        service,
        platform,
        current.mainVersion,
        current.minorVersion,
        current.patchNumber,
    )

    log.debug(
        "Loading exact client version policy row: service={}, platform={}, clientVersion={}.{}.{}",
        service,
        platform,
        parsedVersion.major,
        parsedVersion.minor,
        parsedVersion.patch,
    )
    val userVersionPolicy =
        serviceVersionRepository
            .findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
                service = service,
                platform = platform,
                mainVersion = parsedVersion.major,
                minorVersion = parsedVersion.minor,
                patchNumber = parsedVersion.patch,
            )
    if (userVersionPolicy == null) {
      log.info(
          "Client version is not explicitly registered; continuing with rollout-safe semantic evaluation: service={}, platform={}, appVersion={}",
          service,
          platform,
          request.appVersion,
      )
    } else {
      log.debug(
          "Loaded exact client version policy row: service={}, platform={}, userVersion={}.{}.{}",
          service,
          platform,
          userVersionPolicy.mainVersion,
          userVersionPolicy.minorVersion,
          userVersionPolicy.patchNumber,
      )
    }

    log.debug(
        "Loading required-update target above client version: service={}, platform={}, clientVersion={}.{}.{}",
        service,
        platform,
        parsedVersion.major,
        parsedVersion.minor,
        parsedVersion.patch,
    )
    val requiredTarget =
        serviceVersionRepository
            .findRequiredUpdateTargetsHigherThan(
                service = service,
                platform = platform,
                mainVersion = parsedVersion.major,
                minorVersion = parsedVersion.minor,
                patchNumber = parsedVersion.patch,
                pageable = PageRequest.of(0, 1),
            )
            .firstOrNull()

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
        userVersion = userVersionPolicy?.let(::toVersionString) ?: toVersionString(parsedVersion),
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

  private fun toVersionString(version: ServiceVersionEntity): String {
    return "${version.mainVersion}.${version.minorVersion}.${version.patchNumber}"
  }

  private fun toVersionString(version: SemanticVersion): String {
    return "${version.major}.${version.minor}.${version.patch}"
  }

  private data class SemanticVersion(
      val major: Int,
      val minor: Int,
      val patch: Int,
  )
}
