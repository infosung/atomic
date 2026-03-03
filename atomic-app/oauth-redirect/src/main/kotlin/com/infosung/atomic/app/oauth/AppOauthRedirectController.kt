package com.infosung.atomic.app.oauth

import com.infosung.atomic.contract.exception.HttpStatusException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/** Common OAuth redirect/callback endpoints that return frontend redirect with relayCode. */
@Controller
class AppOauthRedirectController(
    private val appOauthRedirectService: AppOauthRedirectService,
) {
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
  ): String {
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
  ): String {
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
  ): String {
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
        )
    return "redirect:$frontendRedirectUrl"
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
