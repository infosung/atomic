package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.`in`.web.OauthRedirectControllerWebAdapter
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/** Common OAuth redirect/callback endpoints that return frontend redirect with relayCode. */
@Controller
class AppOauthRedirectController(
    appOauthRedirectService: AppOauthRedirectService,
    properties: AtomicAppOauthRedirectProperties,
) {
  private val webAdapter =
      OauthRedirectControllerWebAdapter(
          appOauthRedirectService = appOauthRedirectService,
          properties = properties,
      )

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
    return webAdapter.handleRedirect(
        provider = provider,
        redirectUri = redirectUri,
        nonce = nonce,
        prompt = prompt,
        loginHint = loginHint,
        responseMode = responseMode,
        request = request,
        response = response,
    )
  }

  @GetMapping("\${atomic.app.oauth.redirect.callback-endpoint-path:/oauth/callback}/{provider}")
  fun callback(
      @PathVariable("provider") provider: String,
      @RequestParam("code") code: String,
      @RequestParam("state") state: String,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ): String {
    return webAdapter.handleCallback(
        provider = provider,
        code = code,
        state = state,
        request = request,
        response = response,
    )
  }

  @GetMapping("\${atomic.app.oauth.redirect.callback-endpoint-path:/oauth/callback}/apple")
  fun callbackAppleGet(): String {
    return webAdapter.handleCallbackAppleGet()
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
    return webAdapter.handleCallbackApple(
        state = state,
        idToken = idToken,
        code = code,
        user = user,
        request = request,
        response = response,
    )
  }
}
