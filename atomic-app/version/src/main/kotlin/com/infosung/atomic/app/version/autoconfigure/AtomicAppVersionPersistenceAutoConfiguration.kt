package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.app.version.adapter.out.persistence.JpaLoadVersionPolicyAdapter
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionEntity
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository
import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate

/** Persistence app-version auto-configuration backed by JPA and schema preflight. */
@AutoConfiguration
@ConditionalOnClass(
    name =
        [
            "org.springframework.data.jpa.repository.JpaRepository",
            "jakarta.persistence.Entity",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.app.version",
    name = ["enabled"],
    havingValue = "true",
)
@EntityScan(basePackageClasses = [ServiceVersionEntity::class])
@EnableJpaRepositories(basePackageClasses = [ServiceVersionRepository::class])
class AtomicAppVersionPersistenceAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  internal fun loadVersionPolicyPort(
      serviceVersionRepository: ServiceVersionRepository,
  ): LoadVersionPolicyPort {
    return JpaLoadVersionPolicyAdapter(serviceVersionRepository = serviceVersionRepository)
  }

  @Bean
  @ConditionalOnMissingBean
  fun appVersionSchemaUpgradePreflight(
      jdbcTemplate: JdbcTemplate,
  ): AppVersionSchemaUpgradePreflight {
    return AppVersionSchemaUpgradePreflight(jdbcTemplate = jdbcTemplate)
  }
}
