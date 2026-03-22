package com.infosung.atomic.app.oauth.adapter.`in`.web

import com.infosung.atomic.app.oauth.application.exception.OauthRedirectApplicationException
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectErrorCode
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRemoteFailureException
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.exception.HttpIOException
import com.infosung.atomic.oauth.exception.OauthException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.SecureRandom
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/** Web adapter for the app OAuth redirect/callback relay endpoints. */
@Controller
class AppOauthRedirectController(
    private val buildAuthorizationRedirectUseCase: BuildAuthorizationRedirectUseCase,
    private val buildOauthCallbackRedirectUseCase: BuildOauthCallbackRedirectUseCase,
    private val buildAppleCallbackRedirectUseCase: BuildAppleCallbackRedirectUseCase,
    private val properties: AtomicAppOauthRedirectProperties,
) {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val secureRandom = SecureRandom()

  @GetMapping("\${atomic.app.oauth.redirect.redirect-endpoint-path:/oauth/redirect}/{provider}")
  fun redirect(
      @PathVariable("provider") provider: String,
      @RequestParam("redirectUri") redirectUri: String,
      @RequestParam(name = "nonce", required = false) nonce: String?,
      @RequestParam(name = "prompt", required = false) prompt: String?,
      @RequestParam(name = "loginHint", required = false) loginHint: String?,
      @RequestParam(name = "responseMode", required = false) responseMode: String?,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): String {
    val callbackBindingMode = properties.callbackBinding.resolvedMode()
    val callbackBindingToken = resolveCallbackBindingTokenForRedirect(request)
    val additionalParameters =
        readAdditionalParameters(
            request = request,
            reservedKeys = setOf("redirectUri", "nonce", "prompt", "loginHint", "responseMode"),
        )
    val authorization =
        mapApplicationErrors(provider = provider, action = "oauth authorization redirect") {
          buildAuthorizationRedirectUseCase.build(
              provider = provider,
              redirectUri = redirectUri,
              nonce = nonce,
              prompt = prompt,
              loginHint = loginHint,
              responseMode = responseMode,
              additionalParameters = additionalParameters,
              callbackBindingToken = callbackBindingToken.token,
          )
        }
    if (callbackBindingToken.shouldSetCookie) {
      setCallbackBindingTokenIfEnabled(response = response, token = callbackBindingToken.token)
    }
    log.debug(
        "OAuth redirect completed at web adapter: provider={}, callbackBindingMode={}, callbackBindingCookieIssued={}, redirectTargetType={}, additionalParameterKeys={}",
        provider,
        callbackBindingMode,
        callbackBindingToken.shouldSetCookie,
        authorization.redirectTargetType,
        additionalParameters.keys.sorted(),
    )
    return "redirect:${authorization.authorizationUrl}"
  }

  @GetMapping("\${atomic.app.oauth.redirect.callback-endpoint-path:/oauth/callback}/{provider}")
  fun callback(
      @PathVariable("provider") provider: String,
      @RequestParam("code") code: String,
      @RequestParam("state") state: String,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): String {
    val callbackBindingMode = properties.callbackBinding.resolvedMode()
    val callbackBindingToken = readCallbackBindingTokenIfEnabled(request)
    val additionalParameters =
        readAdditionalParameters(
            request = request,
            reservedKeys = setOf("code", "state"),
        )
    val result =
        mapCallbackErrors(provider = provider) {
          buildOauthCallbackRedirectUseCase.build(
              provider = provider,
              code = code,
              state = state,
              additionalParameters = additionalParameters,
              callbackBindingToken = callbackBindingToken,
          )
        }
    clearCallbackBindingTokenIfEnabled(response)
    log.debug(
        "OAuth callback completed at web adapter: provider={}, callbackBindingMode={}, callbackBindingCookieCleared={}, redirectTargetType={}, additionalParameterKeys={}",
        provider,
        callbackBindingMode,
        properties.callbackBinding.shouldClearCookieOnSuccess(),
        result.redirectTargetType,
        additionalParameters.keys.sorted(),
    )
    return "redirect:${result.frontendRedirectUrl}"
  }

  @GetMapping("\${atomic.app.oauth.redirect.callback-endpoint-path:/oauth/callback}/apple")
  fun callbackAppleGet(): String {
    val errorCode = OauthRedirectErrorCode.OAUTH_APPLE_CALLBACK_POST_ONLY
    throw HttpStatusException(
        status = errorCode.defaultHttpStatus,
        code = errorCode.name,
        message = errorCode.defaultMessage,
    )
  }

  @PostMapping(
      "\${atomic.app.oauth.redirect.callback-endpoint-path:/oauth/callback}/apple",
      consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
  )
  fun callbackApple(
      @RequestParam("state") state: String,
      @RequestParam("id_token") idToken: String,
      @RequestParam(name = "code", required = false) code: String?,
      @RequestParam(name = "user", required = false) user: String?,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): String {
    val callbackBindingMode = properties.callbackBinding.resolvedMode()
    val callbackBindingToken = readCallbackBindingTokenIfEnabled(request)
    val additionalParameters =
        readAdditionalParameters(
            request = request,
            reservedKeys = setOf("state", "id_token", "code", "user"),
        )
    val result =
        mapCallbackErrors(provider = OauthProviderName.APPLE.name) {
          buildAppleCallbackRedirectUseCase.build(
              state = state,
              idToken = idToken,
              code = code,
              user = user,
              additionalParameters = additionalParameters,
              callbackBindingToken = callbackBindingToken,
          )
        }
    clearCallbackBindingTokenIfEnabled(response)
    log.debug(
        "Apple OAuth callback completed at web adapter: callbackBindingMode={}, callbackBindingCookieCleared={}, redirectTargetType={}, additionalParameterKeys={}",
        callbackBindingMode,
        properties.callbackBinding.shouldClearCookieOnSuccess(),
        result.redirectTargetType,
        additionalParameters.keys.sorted(),
    )
    return "redirect:${result.frontendRedirectUrl}"
  }

  private data class CallbackBindingTokenResult(
      val token: String?,
      val shouldSetCookie: Boolean,
  )

  private inline fun <T> mapCallbackErrors(
      provider: String,
      block: () -> T,
  ): T {
    return try {
      block()
    } catch (e: OauthRedirectRemoteFailureException) {
      throwUpstreamCallbackFailure(
          provider = provider,
          code = OauthRedirectErrorCode.OAUTH_PROVIDER_REMOTE_FAILURE,
          cause = e,
      )
    } catch (e: HttpIOException) {
      throwUpstreamCallbackFailure(
          provider = provider,
          code = OauthRedirectErrorCode.OAUTH_PROVIDER_REMOTE_FAILURE,
          cause = e,
      )
    } catch (e: OauthRedirectApplicationException) {
      throwInvalidRequest(
          provider = provider,
          action = "oauth callback",
          code = OauthRedirectErrorCode.OAUTH_CALLBACK_INVALID_REQUEST,
          cause = e,
          fallbackMessage = "Invalid OAuth callback request for provider: $provider",
      )
    } catch (e: HttpStatusException) {
      log.warn(
          "Rejected oauth callback at web adapter: provider={}, message={}", provider, e.message)
      throw e
    } catch (e: OauthException) {
      throwInvalidRequest(
          provider = provider,
          action = "oauth callback",
          code = OauthRedirectErrorCode.OAUTH_CALLBACK_INVALID_REQUEST,
          cause = e,
          fallbackMessage = "Invalid OAuth callback request for provider: $provider",
      )
    } catch (e: IllegalArgumentException) {
      throwInvalidRequest(
          provider = provider,
          action = "oauth callback",
          code = OauthRedirectErrorCode.OAUTH_CALLBACK_INVALID_REQUEST,
          cause = e,
          fallbackMessage = "Invalid OAuth callback request for provider: $provider",
      )
    } catch (e: IllegalStateException) {
      throwConfigurationFailure(
          provider = provider,
          action = "oauth callback",
          cause = e,
      )
    }
  }

  private inline fun <T> mapApplicationErrors(
      provider: String,
      action: String,
      block: () -> T,
  ): T {
    return try {
      block()
    } catch (e: OauthRedirectRemoteFailureException) {
      throwUpstreamApplicationFailure(
          provider = provider,
          action = action,
          code = OauthRedirectErrorCode.OAUTH_PROVIDER_REMOTE_FAILURE,
          cause = e,
      )
    } catch (e: HttpIOException) {
      throwUpstreamApplicationFailure(
          provider = provider,
          action = action,
          code = OauthRedirectErrorCode.OAUTH_PROVIDER_REMOTE_FAILURE,
          cause = e,
      )
    } catch (e: OauthRedirectApplicationException) {
      throwInvalidRequest(
          provider = provider,
          action = action,
          code = OauthRedirectErrorCode.OAUTH_REDIRECT_INVALID_REQUEST,
          cause = e,
          fallbackMessage = "Invalid request for provider: $provider",
      )
    } catch (e: HttpStatusException) {
      log.warn("Rejected {} at web adapter: provider={}, message={}", action, provider, e.message)
      throw e
    } catch (e: IllegalStateException) {
      throwConfigurationFailure(
          provider = provider,
          action = action,
          cause = e,
      )
    }
  }

  private fun throwUpstreamCallbackFailure(
      provider: String,
      code: OauthRedirectErrorCode,
      cause: Exception,
  ): Nothing {
    log.warn(
        "Upstream oauth callback failed at web adapter: provider={}, message={}",
        provider,
        cause.message,
    )
    throw HttpStatusException(
        status = code.defaultHttpStatus,
        code = code.name,
        message = cause.message ?: code.defaultMessage,
        cause = cause,
    )
  }

  private fun throwUpstreamApplicationFailure(
      provider: String,
      action: String,
      code: OauthRedirectErrorCode,
      cause: Exception,
  ): Nothing {
    log.warn(
        "Upstream {} failed at web adapter: provider={}, message={}",
        action,
        provider,
        cause.message,
    )
    throw HttpStatusException(
        status = code.defaultHttpStatus,
        code = code.name,
        message = cause.message ?: code.defaultMessage,
        cause = cause,
    )
  }

  private fun throwInvalidRequest(
      provider: String,
      action: String,
      code: OauthRedirectErrorCode,
      cause: Exception,
      fallbackMessage: String,
  ): Nothing {
    log.warn("Rejected {} at web adapter: provider={}, message={}", action, provider, cause.message)
    throw HttpStatusException(
        status = code.defaultHttpStatus,
        code = code.name,
        message = cause.message ?: fallbackMessage,
        cause = cause,
    )
  }

  private fun throwConfigurationFailure(
      provider: String,
      action: String,
      cause: IllegalStateException,
  ): Nothing {
    val errorCode = OauthRedirectErrorCode.OAUTH_REDIRECT_CONFIGURATION_INVALID
    log.error(
        "OAuth configuration failure at web adapter: action={}, provider={}, message={}",
        action,
        provider,
        cause.message,
        cause,
    )
    throw HttpStatusException(
        status = errorCode.defaultHttpStatus,
        code = errorCode.name,
        message = cause.message ?: errorCode.defaultMessage,
        cause = cause,
    )
  }

  private fun resolveCallbackBindingTokenForRedirect(
      request: HttpServletRequest,
  ): CallbackBindingTokenResult {
    if (!properties.callbackBinding.isCookieValidationEnabled()) {
      log.debug(
          "OAuth redirect callback binding is disabled for this flow: callbackBindingMode={}",
          properties.callbackBinding.resolvedMode(),
      )
      return CallbackBindingTokenResult(token = null, shouldSetCookie = false)
    }
    val existingToken = readCallbackBindingTokenIfEnabled(request)
    if (!existingToken.isNullOrBlank()) {
      log.debug(
          "Reusing existing OAuth callback binding cookie for redirect flow: callbackBindingMode={}",
          properties.callbackBinding.resolvedMode(),
      )
      return CallbackBindingTokenResult(token = existingToken, shouldSetCookie = false)
    }
    log.debug(
        "Issuing new OAuth callback binding cookie for redirect flow: callbackBindingMode={}",
        properties.callbackBinding.resolvedMode(),
    )
    return CallbackBindingTokenResult(
        token = newCallbackBindingToken(),
        shouldSetCookie = true,
    )
  }

  private fun setCallbackBindingTokenIfEnabled(
      response: HttpServletResponse,
      token: String?,
  ) {
    if (!properties.callbackBinding.isCookieValidationEnabled()) {
      return
    }
    val callbackToken =
        token?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OAuth callback binding token is required.")
    val cookie =
        buildCallbackBindingCookie(
            token = callbackToken,
            maxAgeSeconds = properties.callbackBinding.cookieMaxAgeSeconds,
        )
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    log.debug(
        "Set OAuth callback binding cookie: cookieName={}, maxAgeSeconds={}, callbackBindingMode={}",
        resolveCallbackBindingCookieName(),
        properties.callbackBinding.cookieMaxAgeSeconds,
        properties.callbackBinding.resolvedMode(),
    )
  }

  private fun clearCallbackBindingTokenIfEnabled(response: HttpServletResponse) {
    if (!properties.callbackBinding.isCookieValidationEnabled()) {
      return
    }
    if (!properties.callbackBinding.shouldClearCookieOnSuccess()) {
      log.debug(
          "Preserving OAuth callback binding cookie after successful callback: callbackBindingMode={}, cookieName={}",
          properties.callbackBinding.resolvedMode(),
          resolveCallbackBindingCookieName(),
      )
      return
    }
    val cookie =
        buildCallbackBindingCookie(
            token = "",
            maxAgeSeconds = 0,
        )
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    log.debug(
        "Cleared OAuth callback binding cookie after successful callback: callbackBindingMode={}, cookieName={}",
        properties.callbackBinding.resolvedMode(),
        resolveCallbackBindingCookieName(),
    )
  }

  private fun readCallbackBindingTokenIfEnabled(request: HttpServletRequest): String? {
    if (!properties.callbackBinding.isCookieValidationEnabled()) {
      return null
    }
    val cookieName = resolveCallbackBindingCookieName()
    val matchedCookies = request.cookies?.filter { it.name == cookieName }.orEmpty()
    if (matchedCookies.size > 1) {
      log.warn(
          "Rejected OAuth callback due to ambiguous callback binding cookie: cookieName={}",
          cookieName,
      )
      throw HttpStatusException(
          status = OauthRedirectErrorCode.OAUTH_CALLBACK_INVALID_REQUEST.defaultHttpStatus,
          message = "OAuth callback binding cookie is ambiguous.",
          code = OauthRedirectErrorCode.OAUTH_CALLBACK_INVALID_REQUEST.name,
      )
    }
    return matchedCookies.firstOrNull()?.value?.trim()?.takeIf { it.isNotBlank() }
  }

  private fun buildCallbackBindingCookie(
      token: String,
      maxAgeSeconds: Long,
  ): ResponseCookie {
    val cookieName = resolveCallbackBindingCookieName()
    val sameSite = properties.callbackBinding.cookieSameSite.trim().ifBlank { "None" }
    val path = properties.callbackBinding.cookiePath.trim().ifBlank { "/" }
    return ResponseCookie.from(cookieName, token)
        .httpOnly(true)
        .secure(properties.callbackBinding.cookieSecure)
        .sameSite(sameSite)
        .path(path)
        .maxAge(maxAgeSeconds)
        .build()
  }

  private fun resolveCallbackBindingCookieName(): String {
    val cookieName = properties.callbackBinding.cookieName.trim()
    if (cookieName.isBlank()) {
      throw IllegalStateException(
          "atomic.app.oauth.redirect.callback-binding.cookie-name must not be blank when callback binding is enabled.",
      )
    }
    return cookieName
  }

  private fun newCallbackBindingToken(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  private fun readAdditionalParameters(
      request: HttpServletRequest,
      reservedKeys: Set<String>,
  ): Map<String, String> {
    return request.parameterMap.entries
        .asSequence()
        .filterNot { reservedKeys.contains(it.key) }
        .mapNotNull { (key, values) ->
          values.firstOrNull()?.takeIf { it.isNotBlank() }?.let { key to it }
        }
        .toMap()
  }
}
