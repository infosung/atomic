package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.app.version.AppVersionCheckService
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import com.infosung.atomic.app.version.application.service.CheckAppVersionService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** Core app-version auto-configuration that does not require direct JPA access. */
@AutoConfiguration(after = [AtomicAppVersionPersistenceAutoConfiguration::class])
@ConditionalOnProperty(
    prefix = "atomic.app.version",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicAppVersionProperties::class)
class AtomicAppVersionCoreAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(LoadVersionPolicyPort::class)
  internal fun checkAppVersionUseCase(
      loadVersionPolicyPort: LoadVersionPolicyPort,
      properties: AtomicAppVersionProperties,
  ): CheckAppVersionUseCase {
    return CheckAppVersionService(
        loadVersionPolicyPort = loadVersionPolicyPort,
        defaultStoreUrl = properties.defaultStoreUrl,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(CheckAppVersionUseCase::class)
  internal fun appVersionCheckService(
      properties: AtomicAppVersionProperties,
      checkAppVersionUseCase: CheckAppVersionUseCase,
  ): AppVersionCheckService {
    return AppVersionCheckService(
        defaultStoreUrl = properties.defaultStoreUrl,
        checkAppVersionUseCase = checkAppVersionUseCase,
    )
  }
}
