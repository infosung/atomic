package com.infosung.atomic.app.storage.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties

/** Umbrella auto-configuration for common image upload/delete API. */
@AutoConfiguration(
    afterName =
        ["com.infosung.atomic.starter.autoconfigure.storage.AtomicStorageAutoConfiguration"],
)
@ConditionalOnClass(
    name =
        [
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.web.multipart.MultipartFile",
            "org.springframework.data.jpa.repository.JpaRepository",
            "jakarta.persistence.Entity",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.app.image",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicAppImageProperties::class)
@ImportAutoConfiguration(
    value =
        [
            AtomicAppImagePersistenceAutoConfiguration::class,
            AtomicAppImageCoreAutoConfiguration::class,
            AtomicAppImageWebAutoConfiguration::class,
        ],
)
class AtomicAppImageAutoConfiguration
