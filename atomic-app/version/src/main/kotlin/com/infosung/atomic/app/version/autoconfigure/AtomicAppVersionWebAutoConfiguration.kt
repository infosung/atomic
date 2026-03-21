package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionController
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

/** Web app-version auto-configuration that exports the HTTP compatibility seam. */
@AutoConfiguration(after = [AtomicAppVersionCoreAutoConfiguration::class])
@ConditionalOnClass(name = ["org.springframework.web.bind.annotation.RestController"])
@ConditionalOnProperty(
    prefix = "atomic.app.version",
    name = ["enabled"],
    havingValue = "true",
)
class AtomicAppVersionWebAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(CheckAppVersionUseCase::class)
  fun appVersionController(
      checkAppVersionUseCase: CheckAppVersionUseCase,
  ): AppVersionController {
    return AppVersionController(checkAppVersionUseCase = checkAppVersionUseCase)
  }
}
