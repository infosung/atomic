package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.app.version.AppVersionCheckService
import com.infosung.atomic.app.version.AppVersionController
import com.infosung.atomic.app.version.AppVersionHttpExceptionHandler
import com.infosung.atomic.app.version.ServiceVersionEntity
import com.infosung.atomic.app.version.ServiceVersionRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate

/** Auto-configuration for common app version API. */
@AutoConfiguration
@ConditionalOnClass(
    name =
        [
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.data.jpa.repository.JpaRepository",
            "jakarta.persistence.Entity",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.app.version",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicAppVersionProperties::class)
@EntityScan(basePackageClasses = [ServiceVersionEntity::class])
@EnableJpaRepositories(basePackageClasses = [ServiceVersionRepository::class])
class AtomicAppVersionAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun appVersionCheckService(
      serviceVersionRepository: ServiceVersionRepository,
      properties: AtomicAppVersionProperties,
  ): AppVersionCheckService {
    return AppVersionCheckService(
        serviceVersionRepository = serviceVersionRepository,
        defaultStoreUrl = properties.defaultStoreUrl,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun appVersionSchemaUpgradePreflight(
      jdbcTemplate: JdbcTemplate,
  ): AppVersionSchemaUpgradePreflight {
    return AppVersionSchemaUpgradePreflight(jdbcTemplate = jdbcTemplate)
  }

  @Bean
  @ConditionalOnMissingBean
  fun appVersionController(
      appVersionCheckService: AppVersionCheckService,
  ): AppVersionController {
    return AppVersionController(appVersionCheckService = appVersionCheckService)
  }

  @Bean
  @ConditionalOnMissingBean
  fun appVersionHttpExceptionHandler(): AppVersionHttpExceptionHandler {
    return AppVersionHttpExceptionHandler()
  }
}
