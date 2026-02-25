package com.infosung.atomic.oauth.exception

import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName

open class OauthException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause)

class UnsupportedOauthOperationException(
    val provider: OauthProviderName,
    val capability: OauthProviderCapability,
) :
    OauthException(
        message = "Unsupported OAuth capability: ${provider.name}.${capability.name}",
    ) {
  companion object {
    fun fromCapability(
        provider: OauthProviderName,
        capability: OauthProviderCapability,
    ): UnsupportedOauthOperationException {
      return UnsupportedOauthOperationException(
          provider = provider,
          capability = capability,
      )
    }
  }
}

class InvalidOauthRequestException(
    message: String,
    cause: Throwable? = null,
) : OauthException(message = message, cause = cause)
