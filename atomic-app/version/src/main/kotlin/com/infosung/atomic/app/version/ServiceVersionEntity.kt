package com.infosung.atomic.app.version

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/** Version policy row per service/platform/app version. */
@Entity(name = "service_version")
@Table(
    name = "service_version",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uq_service_version_service_platform_semver",
                columnNames =
                    ["service", "platform", "main_version", "minor_version", "patch_number"],
            ),
        ],
)
open class ServiceVersionEntity(
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long? = null,
    @Column(name = "main_version") open val mainVersion: Int = 0,
    @Column(name = "minor_version") open val minorVersion: Int = 0,
    @Column(name = "patch_number") open val patchNumber: Int = 0,
    @Column(name = "require_update") open val requireUpdate: Boolean = false,
    @Column(name = "platform") open val platform: String = "ANDROID",
    @Column(name = "service") open val service: String = "DEFAULT",
    @Column(name = "store_url") open val storeUrl: String? = null,
    @Column(name = "created_at") open val createdAt: LocalDateTime = LocalDateTime.now(),
) {
  @Column(name = "store_available") open var storeAvailable: Boolean = true
}
