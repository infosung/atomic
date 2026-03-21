package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.adapter.`in`.web.AppOauthRedirectController
import com.infosung.atomic.app.oauth.adapter.`in`.web.AppOauthRedirectHttpExceptionHandler
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

/** Web auto-configuration that exports oauth redirect HTTP entrypoints. */
@AutoConfiguration(after = [AtomicAppOauthRedirectCoreAutoConfiguration::class])
@ConditionalOnClass(name = ["org.springframework.stereotype.Controller"])
@ConditionalOnProperty(
    prefix = "atomic.app.oauth.redirect",
    name = ["enabled"],
    havingValue = "true",
)
class AtomicAppOauthRedirectWebAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(
      BuildAuthorizationRedirectUseCase::class,
      BuildOauthCallbackRedirectUseCase::class,
      BuildAppleCallbackRedirectUseCase::class,
  )
  fun appOauthRedirectController(
      buildAuthorizationRedirectUseCase: BuildAuthorizationRedirectUseCase,
      buildOauthCallbackRedirectUseCase: BuildOauthCallbackRedirectUseCase,
      buildAppleCallbackRedirectUseCase: BuildAppleCallbackRedirectUseCase,
      properties: AtomicAppOauthRedirectProperties,
  ): AppOauthRedirectController {
    return AppOauthRedirectController(
        buildAuthorizationRedirectUseCase = buildAuthorizationRedirectUseCase,
        buildOauthCallbackRedirectUseCase = buildOauthCallbackRedirectUseCase,
        buildAppleCallbackRedirectUseCase = buildAppleCallbackRedirectUseCase,
        properties = properties,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(AppOauthRedirectController::class)
  fun appOauthRedirectHttpExceptionHandler(): AppOauthRedirectHttpExceptionHandler {
    return AppOauthRedirectHttpExceptionHandler()
  }
}
