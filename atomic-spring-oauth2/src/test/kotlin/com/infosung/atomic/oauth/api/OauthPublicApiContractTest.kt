package com.infosung.atomic.oauth.api

import com.infosung.atomic.oauth.idtoken.IdTokenParser
import com.infosung.atomic.oauth.idtoken.OauthIdTokenClaims
import com.infosung.atomic.oauth.state.OauthStateClaims
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
            "codeChallenge",
            "codeChallengeMethod",
            "prompt",
            "loginHint",
            "responseMode",
            "stateAttributes",
            "additionalParameters",
        ),
    )
    assertConstructorFields(
        OauthTokenExchangeRequest::class,
        listOf("code", "state", "codeVerifier", "scopes", "scopePreset", "additionalParameters"),
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
            "providerSubject",
            "email",
            "emailVerified",
            "displayName",
            "pictureUrl",
            "selectedClientKey",
            "scopes",
            "payloadMode",
            "normalizedProfileMetadata",
            "claims",
            "rawProfile",
        ),
    )
    assertConstructorFields(
        OauthStateClaims::class,
        listOf(
            "issuer",
            "stateId",
            "issuedAt",
            "expiresAt",
            "provider",
            "redirectUri",
            "nonce",
            "attributes"),
    )
    assertConstructorFields(
        OauthIdTokenClaims::class,
        listOf("issuer", "subject", "audiences", "issuedAt", "expiresAt", "nonce", "claims"),
    )
  }

  @Test
  fun `oauth service provider, state manager, and id token parser public methods should remain stable`() {
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
            "readStateClaims(String, OauthProviderName, String, String):OauthStateClaims",
            "verifyState(String, OauthProviderName, String, String):Jwt",
            "verifyStateClaims(String, OauthProviderName, String, String):OauthStateClaims",
        ),
        publicSignatures(OauthStateManager::class.java),
    )
    assertEquals(
        listOf(
            "verifyIdToken(String, String, String):Jwt",
            "verifyIdTokenClaims(String, String, String):OauthIdTokenClaims",
        ),
        publicSignatures(IdTokenParser::class.java),
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
