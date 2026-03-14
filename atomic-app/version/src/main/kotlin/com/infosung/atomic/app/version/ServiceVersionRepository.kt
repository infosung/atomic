package com.infosung.atomic.app.version

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** Repository for service version policies. */
interface ServiceVersionRepository : JpaRepository<ServiceVersionEntity, Long> {
  /** Reads all versions for one service/platform ordered by descending semantic version. */
  fun findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
      service: String,
      platform: String,
  ): List<ServiceVersionEntity>

  /** Reads the latest policy row for one service/platform. */
  fun findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
      service: String,
      platform: String,
  ): ServiceVersionEntity?

  /** Reads the exact client version policy row for one service/platform/version. */
  fun findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
      service: String,
      platform: String,
      mainVersion: Int,
      minorVersion: Int,
      patchNumber: Int,
  ): ServiceVersionEntity?

  /** Reads higher required-update targets for one service/platform above the client version. */
  @Query(
      """
      SELECT sv
      FROM service_version sv
      WHERE sv.service = :service
        AND sv.platform = :platform
        AND sv.requireUpdate = true
        AND (
          sv.mainVersion > :mainVersion
          OR (sv.mainVersion = :mainVersion AND sv.minorVersion > :minorVersion)
          OR (
            sv.mainVersion = :mainVersion
            AND sv.minorVersion = :minorVersion
            AND sv.patchNumber > :patchNumber
          )
        )
      ORDER BY sv.mainVersion DESC, sv.minorVersion DESC, sv.patchNumber DESC
      """,
  )
  fun findRequiredUpdateTargetsHigherThan(
      @Param("service") service: String,
      @Param("platform") platform: String,
      @Param("mainVersion") mainVersion: Int,
      @Param("minorVersion") minorVersion: Int,
      @Param("patchNumber") patchNumber: Int,
      pageable: Pageable,
  ): List<ServiceVersionEntity>
}
