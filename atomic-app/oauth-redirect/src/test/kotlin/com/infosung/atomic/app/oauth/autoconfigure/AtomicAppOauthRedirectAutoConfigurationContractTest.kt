package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.AppOauthRedirectController
import com.infosung.atomic.app.oauth.AppOauthRedirectHttpExceptionHandler
import com.infosung.atomic.app.oauth.AppOauthRedirectService
import com.infosung.atomic.app.oauth.AppOauthRelayCodeService
import com.infosung.atomic.app.oauth.adapter.out.oauth.OauthServiceProviderAdapter
import com.infosung.atomic.app.oauth.adapter.out.redirect.AllowedRedirectUriPortAdapter
import com.infosung.atomic.app.oauth.adapter.out.relay.AppOauthRelayCodePortAdapter
import com.infosung.atomic.app.oauth.adapter.out.state.OauthStateManagerAdapter
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.app.oauth.application.service.BuildAppleCallbackRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildAuthorizationRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildOauthCallbackRedirectService
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.state.OauthStateManager
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean

class AtomicAppOauthRedirectAutoConfigurationContractTest {
  @Test
  fun `auto configuration factory methods should create ports use-cases facade controller and handler`() {
    val autoConfiguration = AtomicAppOauthRedirectAutoConfiguration()
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthStateManager = mock(OauthStateManager::class.java)
    val relayCodeService = mock(AppOauthRelayCodeService::class.java)
    val properties =
        AtomicAppOauthRedirectProperties().apply {
          enabled = true
          allowedRedirectUriPrefixes = listOf("https://app.example.com/oauth")
        }

    val providerPort = autoConfiguration.oauthProviderOperationsPort(oauthServiceProvider)
    val statePort = autoConfiguration.verifyOauthStatePort(oauthStateManager)
    val relayPort = autoConfiguration.issueOauthRelayCodePort(relayCodeService)
    val redirectPort = autoConfiguration.validateOauthRedirectUriPort(properties)
    val authorizationUseCase =
        autoConfiguration.buildAuthorizationRedirectUseCase(
            providerPort,
            redirectPort,
            properties,
        )
    val callbackUseCase =
        autoConfiguration.buildOauthCallbackRedirectUseCase(
            providerPort,
            statePort,
            relayPort,
            redirectPort,
            properties,
        )
    val appleCallbackUseCase =
        autoConfiguration.buildAppleCallbackRedirectUseCase(
            providerPort,
            statePort,
            relayPort,
            redirectPort,
            properties,
        )
    val service =
        autoConfiguration.appOauthRedirectService(
            oauthServiceProvider,
            oauthStateManager,
            relayCodeService,
            properties,
            authorizationUseCase,
            callbackUseCase,
            appleCallbackUseCase,
        )
    val controller = autoConfiguration.appOauthRedirectController(service, properties)
    val handler = autoConfiguration.appOauthRedirectHttpExceptionHandler()

    assertIs<OauthProviderOperationsPort>(providerPort)
    assertIs<OauthServiceProviderAdapter>(providerPort)
    assertIs<VerifyOauthStatePort>(statePort)
    assertIs<OauthStateManagerAdapter>(statePort)
    assertIs<IssueOauthRelayCodePort>(relayPort)
    assertIs<AppOauthRelayCodePortAdapter>(relayPort)
    assertIs<ValidateOauthRedirectUriPort>(redirectPort)
    assertIs<AllowedRedirectUriPortAdapter>(redirectPort)
    assertIs<BuildAuthorizationRedirectUseCase>(authorizationUseCase)
    assertIs<BuildAuthorizationRedirectService>(authorizationUseCase)
    assertIs<BuildOauthCallbackRedirectUseCase>(callbackUseCase)
    assertIs<BuildOauthCallbackRedirectService>(callbackUseCase)
    assertIs<BuildAppleCallbackRedirectUseCase>(appleCallbackUseCase)
    assertIs<BuildAppleCallbackRedirectService>(appleCallbackUseCase)
    assertIs<AppOauthRedirectService>(service)
    assertIs<AppOauthRedirectController>(controller)
    assertIs<AppOauthRedirectHttpExceptionHandler>(handler)
  }

  @Test
  fun `auto configuration should keep oauth redirect facade override guard on exported bean`() {
    val beanMethod =
        AtomicAppOauthRedirectAutoConfiguration::class
            .java
            .declaredMethods
            .single(::isOauthRedirectServiceBeanMethod)

    assertTrue(beanMethod.isAnnotationPresent(ConditionalOnMissingBean::class.java))
    assertEquals(AppOauthRedirectService::class.java, beanMethod.returnType)
  }

  private fun isOauthRedirectServiceBeanMethod(method: Method): Boolean {
    return method.name.startsWith("appOauthRedirectService") &&
        method.returnType == AppOauthRedirectService::class.java &&
        method.parameterTypes.contentEquals(
            arrayOf(
                OauthServiceProvider::class.java,
                OauthStateManager::class.java,
                AppOauthRelayCodeService::class.java,
                AtomicAppOauthRedirectProperties::class.java,
                BuildAuthorizationRedirectUseCase::class.java,
                BuildOauthCallbackRedirectUseCase::class.java,
                BuildAppleCallbackRedirectUseCase::class.java,
            ),
        )
  }
}
