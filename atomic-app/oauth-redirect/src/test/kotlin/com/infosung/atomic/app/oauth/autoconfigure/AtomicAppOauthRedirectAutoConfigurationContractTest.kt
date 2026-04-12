package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.adapter.`in`.web.AppOauthRedirectController
import com.infosung.atomic.app.oauth.adapter.out.oauth.OauthServiceProviderAdapter
import com.infosung.atomic.app.oauth.adapter.out.redirect.AllowedRedirectUriPortAdapter
import com.infosung.atomic.app.oauth.adapter.out.relay.IssueOauthRelayCodeUseCasePortAdapter
import com.infosung.atomic.app.oauth.adapter.out.relay.OauthRelayCodeStorePortAdapter
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.state.OauthStateManagerAdapter
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.ConsumeOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.StoreOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.app.oauth.application.service.BuildAppleCallbackRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildAuthorizationRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildOauthCallbackRedirectService
import com.infosung.atomic.app.oauth.application.service.ConsumeOauthRelayCodeService
import com.infosung.atomic.app.oauth.application.service.IssueOauthRelayCodeService
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.state.OauthStateManager
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Import

class AtomicAppOauthRedirectAutoConfigurationContractTest {
  @Test
  fun `umbrella auto configuration should import relay core and web splits`() {
    val imported =
        AtomicAppOauthRedirectAutoConfiguration::class
            .java
            .getAnnotation(Import::class.java)
            .value
            .toSet()

    assertEquals(
        setOf(
            AtomicAppOauthRedirectPersistenceAutoConfiguration::class,
            AtomicAppOauthRedirectRelayAutoConfiguration::class,
            AtomicAppOauthRedirectCoreAutoConfiguration::class,
            AtomicAppOauthRedirectWebAutoConfiguration::class,
        ),
        imported,
    )
  }

  @Test
  fun `split auto configuration factory methods should create stores ports use cases and web adapters`() {
    val relayAutoConfiguration = AtomicAppOauthRedirectRelayAutoConfiguration()
    val coreAutoConfiguration = AtomicAppOauthRedirectCoreAutoConfiguration()
    val webAutoConfiguration = AtomicAppOauthRedirectWebAutoConfiguration()
    val oauthServiceProvider: OauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthStateManager: OauthStateManager = mock(OauthStateManager::class.java)
    val relayCodeStore: OauthRelayCodeStore = mock(OauthRelayCodeStore::class.java)
    val timeProviderProvider = StaticListableBeanFactory().getBeanProvider(TimeProvider::class.java)
    val properties =
        AtomicAppOauthRedirectProperties().apply {
          enabled = true
          allowedRedirectUriPrefixes = listOf("https://app.example.com/oauth")
        }

    val providerPort = coreAutoConfiguration.oauthProviderOperationsPort(oauthServiceProvider)
    val statePort = coreAutoConfiguration.verifyOauthStatePort(oauthStateManager)
    val relayStorePort = relayAutoConfiguration.storeOauthRelayCodePort(relayCodeStore)
    val issueRelayUseCase =
        relayAutoConfiguration.issueOauthRelayCodeUseCase(
            relayStorePort,
            properties,
            timeProviderProvider,
        )
    val consumeRelayUseCase =
        relayAutoConfiguration.consumeOauthRelayCodeUseCase(
            relayStorePort,
            timeProviderProvider,
        )
    val relayIssuePort = coreAutoConfiguration.issueOauthRelayCodePort(issueRelayUseCase)
    val redirectPort = coreAutoConfiguration.validateOauthRedirectUriPort(properties)
    val authorizationUseCase =
        coreAutoConfiguration.buildAuthorizationRedirectUseCase(
            providerPort,
            redirectPort,
            properties,
        )
    val callbackUseCase =
        coreAutoConfiguration.buildOauthCallbackRedirectUseCase(
            providerPort,
            statePort,
            relayIssuePort,
            redirectPort,
            properties,
        )
    val appleCallbackUseCase =
        coreAutoConfiguration.buildAppleCallbackRedirectUseCase(
            providerPort,
            statePort,
            relayIssuePort,
            redirectPort,
            properties,
        )
    val controller =
        webAutoConfiguration.appOauthRedirectController(
            authorizationUseCase,
            callbackUseCase,
            appleCallbackUseCase,
            properties,
        )

    assertIs<OauthProviderOperationsPort>(providerPort)
    assertIs<OauthServiceProviderAdapter>(providerPort)
    assertIs<VerifyOauthStatePort>(statePort)
    assertIs<OauthStateManagerAdapter>(statePort)
    assertIs<StoreOauthRelayCodePort>(relayStorePort)
    assertIs<OauthRelayCodeStorePortAdapter>(relayStorePort)
    assertIs<IssueOauthRelayCodeUseCase>(issueRelayUseCase)
    assertIs<IssueOauthRelayCodeService>(issueRelayUseCase)
    assertIs<ConsumeOauthRelayCodeUseCase>(consumeRelayUseCase)
    assertIs<ConsumeOauthRelayCodeService>(consumeRelayUseCase)
    assertIs<IssueOauthRelayCodePort>(relayIssuePort)
    assertIs<IssueOauthRelayCodeUseCasePortAdapter>(relayIssuePort)
    assertIs<ValidateOauthRedirectUriPort>(redirectPort)
    assertIs<AllowedRedirectUriPortAdapter>(redirectPort)
    assertIs<BuildAuthorizationRedirectUseCase>(authorizationUseCase)
    assertIs<BuildAuthorizationRedirectService>(authorizationUseCase)
    assertIs<BuildOauthCallbackRedirectUseCase>(callbackUseCase)
    assertIs<BuildOauthCallbackRedirectService>(callbackUseCase)
    assertIs<BuildAppleCallbackRedirectUseCase>(appleCallbackUseCase)
    assertIs<BuildAppleCallbackRedirectService>(appleCallbackUseCase)
    assertIs<AppOauthRedirectController>(controller)
  }

  @Test
  fun `relay auto configuration should keep override guards on supported relay seams`() {
    assertTrue(
        relayBeanMethod("oauthRelayCodeStore", OauthRelayCodeStore::class.java)
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
    assertEquals(
        OauthRelayCodeStore::class.java,
        relayBeanMethod("oauthRelayCodeStore", OauthRelayCodeStore::class.java).returnType,
    )

    assertTrue(
        relayBeanMethod("issueOauthRelayCodeUseCase", IssueOauthRelayCodeUseCase::class.java)
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
    assertEquals(
        IssueOauthRelayCodeUseCase::class.java,
        relayBeanMethod("issueOauthRelayCodeUseCase", IssueOauthRelayCodeUseCase::class.java)
            .returnType,
    )

    assertTrue(
        relayBeanMethod("consumeOauthRelayCodeUseCase", ConsumeOauthRelayCodeUseCase::class.java)
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
    assertEquals(
        ConsumeOauthRelayCodeUseCase::class.java,
        relayBeanMethod(
                "consumeOauthRelayCodeUseCase",
                ConsumeOauthRelayCodeUseCase::class.java,
            )
            .returnType,
    )
  }

  @Test
  fun `web auto configuration should keep override guards on exported web adapters`() {
    assertTrue(
        webBeanMethod("appOauthRedirectController")
            .isAnnotationPresent(ConditionalOnMissingBean::class.java),
    )
    assertEquals(
        AppOauthRedirectController::class.java,
        webBeanMethod("appOauthRedirectController").returnType,
    )
  }

  private fun relayBeanMethod(name: String, returnType: Class<*>): Method {
    return AtomicAppOauthRedirectRelayAutoConfiguration::class.java.declaredMethods.first {
      it.name.startsWith(name) && it.returnType == returnType
    }
  }

  private fun webBeanMethod(name: String): Method {
    return AtomicAppOauthRedirectWebAutoConfiguration::class.java.declaredMethods.first {
      it.name == name
    }
  }
}
