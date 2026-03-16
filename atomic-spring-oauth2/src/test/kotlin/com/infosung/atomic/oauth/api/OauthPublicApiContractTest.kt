package com.infosung.atomic.oauth.api

import com.infosung.atomic.oauth.state.OauthStateManager
import java.lang.reflect.Modifier
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals

class OauthPublicApiContractTest {
  @Test
  fun `oauth request and response model constructors should remain stable`() {
    assertConstructorFields(
        OauthAuthorizationRequest::class,
        listOf(
            "redirectUri",
            "scopes",
            "scopePreset",
            "nonce",
            "prompt",
            "loginHint",
            "responseMode",
            "stateAttributes",
            "additionalParameters",
        ),
    )
    assertConstructorFields(
        OauthTokenExchangeRequest::class,
        listOf("code", "state", "scopes", "scopePreset", "additionalParameters"),
    )
    assertConstructorFields(
        OauthTokenRefreshRequest::class,
        listOf("refreshToken", "accessToken", "scopes", "scopePreset", "additionalParameters"),
    )
    assertConstructorFields(
        OauthTokenRevokeRequest::class,
        listOf("accessToken", "additionalParameters"),
    )
    assertConstructorFields(
        OauthIdentityRequest::class,
        listOf(
            "strategy",
            "accessToken",
            "idToken",
            "audience",
            "scopes",
            "scopePreset",
            "payloadMode",
            "nonce",
            "userInfoEndpoint",
            "userInfoParameters",
            "additionalParameters",
        ),
    )
    assertConstructorFields(
        OauthTokenResult::class,
        listOf(
            "accessToken",
            "refreshToken",
            "idToken",
            "tokenType",
            "expiresInSeconds",
            "scopes",
            "raw",
        ),
    )
    assertConstructorFields(
        OauthIdentityResult::class,
        listOf(
            "provider",
            "userId",
            "email",
            "displayName",
            "pictureUrl",
            "scopes",
            "payloadMode",
            "claims",
            "rawProfile",
        ),
    )
  }

  @Test
  fun `oauth service provider and state manager public methods should remain stable`() {
    assertEquals(
        listOf(
            "getService(OauthProviderName):OauthProvider",
            "getService(String):OauthProvider",
            "requireService(OauthProviderName):OauthProvider",
        ),
        publicSignatures(OauthServiceProvider::class.java),
    )
    assertEquals(
        listOf(
            "isReplayProtectionEnabled():boolean",
            "issueState(OauthProviderName, String, String, Map):String",
            "readState(String, OauthProviderName, String, String):Jwt",
            "verifyState(String, OauthProviderName, String, String):Jwt",
        ),
        publicSignatures(OauthStateManager::class.java),
    )
  }

  private fun assertConstructorFields(
      type: kotlin.reflect.KClass<*>,
      expected: List<String>,
  ) {
    assertEquals(expected, type.primaryConstructor!!.parameters.mapNotNull { it.name })
  }

  private fun publicSignatures(type: Class<*>): List<String> {
    return type.declaredMethods
        .filter {
          Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("\$default")
        }
        .map { method ->
          val parameters = method.parameterTypes.joinToString(", ") { it.simpleName }
          "${method.name}($parameters):${method.returnType.simpleName}"
        }
        .sorted()
  }
}
