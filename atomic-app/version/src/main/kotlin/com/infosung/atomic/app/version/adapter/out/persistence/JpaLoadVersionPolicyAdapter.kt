package com.infosung.atomic.app.version.adapter.out.persistence

import com.infosung.atomic.app.version.ServiceVersionRepository
import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import com.infosung.atomic.app.version.domain.SemanticVersion
import com.infosung.atomic.app.version.domain.VersionPolicy
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest

internal class JpaLoadVersionPolicyAdapter(
    private val serviceVersionRepository: ServiceVersionRepository,
) : LoadVersionPolicyPort {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun loadLatestRegistered(service: String, platform: String): VersionPolicy? {
    log.debug(
        "Loading latest registered version policy from persistence: service={}, platform={}",
        service,
        platform,
    )
    return serviceVersionRepository
        .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
            service,
            platform,
        )
        ?.let(ServiceVersionPersistenceMapper::toDomain)
  }

  override fun loadLatestStoreAvailable(service: String, platform: String): VersionPolicy? {
    log.debug(
        "Loading latest store-available version policy from persistence: service={}, platform={}",
        service,
        platform,
    )
    return serviceVersionRepository
        .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
            service,
            platform,
        )
        ?.let(ServiceVersionPersistenceMapper::toDomain)
  }

  override fun loadExact(
      service: String,
      platform: String,
      version: SemanticVersion,
  ): VersionPolicy? {
    log.debug(
        "Loading exact client version policy from persistence: service={}, platform={}, version={}",
        service,
        platform,
        version,
    )
    return serviceVersionRepository
        .findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
            service = service,
            platform = platform,
            mainVersion = version.major,
            minorVersion = version.minor,
            patchNumber = version.patch,
        )
        ?.let(ServiceVersionPersistenceMapper::toDomain)
  }

  override fun loadRequiredUpdateTargetAbove(
      service: String,
      platform: String,
      version: SemanticVersion,
  ): VersionPolicy? {
    log.debug(
        "Loading required-update target above client version from persistence: service={}, platform={}, version={}",
        service,
        platform,
        version,
    )
    return serviceVersionRepository
        .findRequiredUpdateTargetsHigherThan(
            service = service,
            platform = platform,
            mainVersion = version.major,
            minorVersion = version.minor,
            patchNumber = version.patch,
            pageable = PageRequest.of(0, 1),
        )
        .firstOrNull()
        ?.let(ServiceVersionPersistenceMapper::toDomain)
  }
}
