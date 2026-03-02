package com.infosung.atomic.oauth.exception

import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName

/** Base exception type for OAuth module failures. */
open class OauthException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause)

/** Thrown when requested provider capability is not implemented. */
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

/** Thrown when OAuth request payload is invalid or inconsistent. */
class InvalidOauthRequestException(
    message: String,
    cause: Throwable? = null,
) : OauthException(message = message, cause = cause)
