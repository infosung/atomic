package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.app.version.AppVersionCheckService
import com.infosung.atomic.app.version.AppVersionController
import com.infosung.atomic.app.version.AppVersionHttpExceptionHandler
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
  @ConditionalOnBean(AppVersionCheckService::class)
  fun appVersionController(
      appVersionCheckService: AppVersionCheckService,
  ): AppVersionController {
    return AppVersionController(appVersionCheckService = appVersionCheckService)
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(AppVersionController::class)
  fun appVersionHttpExceptionHandler(): AppVersionHttpExceptionHandler {
    return AppVersionHttpExceptionHandler()
  }
}
