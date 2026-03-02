package com.infosung.atomic.app.version

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

/** Version policy row per service/platform/app version. */
@Entity(name = "service_version")
open class ServiceVersionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) open val id: Long? = null,
    open val mainVersion: Int = 0,
    open val minorVersion: Int = 0,
    open val patchNumber: Int = 0,
    open val requireUpdate: Boolean = false,
    open val platform: String = "ANDROID",
    open val service: String = "DEFAULT",
    open val storeUrl: String? = null,
    open val createdAt: LocalDateTime = LocalDateTime.now(),
)
