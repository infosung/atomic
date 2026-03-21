package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.state.OauthStateManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** Core redirect orchestration auto-configuration for oauth redirect flows. */
@AutoConfiguration(after = [AtomicAppOauthRedirectRelayAutoConfiguration::class])
@ConditionalOnProperty(
    prefix = "atomic.app.oauth.redirect",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicAppOauthRedirectProperties::class)
class AtomicAppOauthRedirectCoreAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(OauthServiceProvider::class)
  internal fun oauthProviderOperationsPort(
      oauthServiceProvider: OauthServiceProvider,
  ): OauthProviderOperationsPort {
    return OauthRedirectComposition.oauthProviderOperationsPort(oauthServiceProvider)
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(OauthStateManager::class)
  internal fun verifyOauthStatePort(
      oauthStateManager: OauthStateManager,
  ): VerifyOauthStatePort {
    return OauthRedirectComposition.verifyOauthStatePort(oauthStateManager)
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(IssueOauthRelayCodeUseCase::class)
  internal fun issueOauthRelayCodePort(
      issueOauthRelayCodeUseCase: IssueOauthRelayCodeUseCase,
  ): IssueOauthRelayCodePort {
    return OauthRedirectComposition.issueOauthRelayCodePort(issueOauthRelayCodeUseCase)
  }

  @Bean
  @ConditionalOnMissingBean
  internal fun validateOauthRedirectUriPort(
      properties: AtomicAppOauthRedirectProperties,
  ): ValidateOauthRedirectUriPort {
    return OauthRedirectComposition.validateOauthRedirectUriPort(properties)
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(OauthProviderOperationsPort::class, ValidateOauthRedirectUriPort::class)
  internal fun buildAuthorizationRedirectUseCase(
      oauthProviderOperationsPort: OauthProviderOperationsPort,
      validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildAuthorizationRedirectUseCase {
    return OauthRedirectComposition.buildAuthorizationRedirectUseCase(
        oauthProviderOperationsPort = oauthProviderOperationsPort,
        validateOauthRedirectUriPort = validateOauthRedirectUriPort,
        properties = properties,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(
      OauthProviderOperationsPort::class,
      VerifyOauthStatePort::class,
      IssueOauthRelayCodePort::class,
      ValidateOauthRedirectUriPort::class,
  )
  internal fun buildOauthCallbackRedirectUseCase(
      oauthProviderOperationsPort: OauthProviderOperationsPort,
      verifyOauthStatePort: VerifyOauthStatePort,
      issueOauthRelayCodePort: IssueOauthRelayCodePort,
      validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildOauthCallbackRedirectUseCase {
    return OauthRedirectComposition.buildOauthCallbackRedirectUseCase(
        oauthProviderOperationsPort = oauthProviderOperationsPort,
        verifyOauthStatePort = verifyOauthStatePort,
        issueOauthRelayCodePort = issueOauthRelayCodePort,
        validateOauthRedirectUriPort = validateOauthRedirectUriPort,
        properties = properties,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(
      OauthProviderOperationsPort::class,
      VerifyOauthStatePort::class,
      IssueOauthRelayCodePort::class,
      ValidateOauthRedirectUriPort::class,
  )
  internal fun buildAppleCallbackRedirectUseCase(
      oauthProviderOperationsPort: OauthProviderOperationsPort,
      verifyOauthStatePort: VerifyOauthStatePort,
      issueOauthRelayCodePort: IssueOauthRelayCodePort,
      validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildAppleCallbackRedirectUseCase {
    return OauthRedirectComposition.buildAppleCallbackRedirectUseCase(
        oauthProviderOperationsPort = oauthProviderOperationsPort,
        verifyOauthStatePort = verifyOauthStatePort,
        issueOauthRelayCodePort = issueOauthRelayCodePort,
        validateOauthRedirectUriPort = validateOauthRedirectUriPort,
        properties = properties,
    )
  }
}
