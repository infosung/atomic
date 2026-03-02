package com.infosung.atomic.app.version

import org.springframework.data.jpa.repository.JpaRepository

/** Repository for service version policies. */
interface ServiceVersionRepository : JpaRepository<ServiceVersionEntity, Long> {
  /** Reads all versions for one service/platform ordered by descending semantic version. */
  fun findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
      service: String,
      platform: String,
  ): List<ServiceVersionEntity>
}
