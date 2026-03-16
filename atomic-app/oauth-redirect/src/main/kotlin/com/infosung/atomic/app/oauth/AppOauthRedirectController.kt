package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
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

/** Common OAuth redirect/callback endpoints that return frontend redirect with relayCode. */
@Controller
class AppOauthRedirectController(
    private val appOauthRedirectService: AppOauthRedirectService,
    private val properties: AtomicAppOauthRedirectProperties,
) {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val secureRandom = SecureRandom()

  /**
   * Redirects user-agent to provider authorization page.
   *
   * Required query:
   * - `redirectUri`: frontend URI where callback result should be redirected.
   */
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
    val authorizationUrl =
        appOauthRedirectService.buildAuthorizationRedirectUrl(
            provider = provider,
            redirectUri = redirectUri,
            nonce = nonce,
            prompt = prompt,
            loginHint = loginHint,
            responseMode = responseMode,
            additionalParameters = additionalParameters,
            callbackBindingToken = callbackBindingToken.token,
        )
    if (callbackBindingToken.shouldSetCookie) {
      setCallbackBindingTokenIfEnabled(response = response, token = callbackBindingToken.token)
    }
    log.debug(
        "OAuth redirect completed: provider={}, callbackBindingMode={}, callbackBindingCookieIssued={}, additionalParameterKeys={}",
        provider,
        callbackBindingMode,
        callbackBindingToken.shouldSetCookie,
        additionalParameters.keys.sorted(),
    )
    return "redirect:$authorizationUrl"
  }

  /**
   * Handles OAuth callback for providers that return `code/state` in query.
   *
   * Frontend receives `relayCode` instead of raw token.
   */
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
    val frontendRedirectUrl =
        appOauthRedirectService.buildCallbackRedirectUrl(
            provider = provider,
            code = code,
            state = state,
            additionalParameters = additionalParameters,
            callbackBindingToken = callbackBindingToken,
        )
    clearCallbackBindingTokenIfEnabled(response)
    log.debug(
        "OAuth callback completed: provider={}, callbackBindingMode={}, callbackBindingCookieCleared={}, additionalParameterKeys={}",
        provider,
        callbackBindingMode,
        properties.callbackBinding.shouldClearCookieOnSuccess(),
        additionalParameters.keys.sorted(),
    )
    return "redirect:$frontendRedirectUrl"
  }

  /** Apple callback does not support GET. Use POST form_post endpoint only. */
  @GetMapping("\${atomic.app.oauth.redirect.callback-endpoint-path:/oauth/callback}/apple")
  fun callbackAppleGet(): String {
    throw HttpStatusException(
        status = 400,
        message = "Apple callback supports POST form_post only.",
    )
  }

  /** Handles Apple callback (`form_post`) and redirects frontend with relayCode. */
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
    val frontendRedirectUrl =
        appOauthRedirectService.buildAppleCallbackRedirectUrl(
            state = state,
            idToken = idToken,
            code = code,
            user = user,
            additionalParameters = additionalParameters,
            callbackBindingToken = callbackBindingToken,
        )
    clearCallbackBindingTokenIfEnabled(response)
    log.debug(
        "Apple OAuth callback completed: callbackBindingMode={}, callbackBindingCookieCleared={}, additionalParameterKeys={}",
        callbackBindingMode,
        properties.callbackBinding.shouldClearCookieOnSuccess(),
        additionalParameters.keys.sorted(),
    )
    return "redirect:$frontendRedirectUrl"
  }

  private data class CallbackBindingTokenResult(
      val token: String?,
      val shouldSetCookie: Boolean,
  )

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
          cookieName)
      throw HttpStatusException(
          status = 400,
          message = "OAuth callback binding cookie is ambiguous.",
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
