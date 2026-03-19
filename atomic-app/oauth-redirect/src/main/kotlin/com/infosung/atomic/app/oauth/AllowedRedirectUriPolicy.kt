package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.redirect.AllowedRedirectUriPolicySupport

internal object AllowedRedirectUriPolicy {
  fun validateConfiguredPrefixes(configuredPrefixes: List<String>) {
    AllowedRedirectUriPolicySupport.validateConfiguredPrefixes(configuredPrefixes)
  }

  fun validateRedirectUri(
      redirectUri: String,
      configuredPrefixes: List<String>,
  ): String {
    return AllowedRedirectUriPolicySupport.validateRedirectUri(
        redirectUri = redirectUri,
        configuredPrefixes = configuredPrefixes,
    )
  }
}
