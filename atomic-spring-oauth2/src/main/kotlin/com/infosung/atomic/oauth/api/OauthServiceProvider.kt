package com.infosung.atomic.oauth.api

import com.infosung.atomic.oauth.exception.OauthException
import java.util.Locale
import org.slf4j.LoggerFactory

class OauthServiceProvider(providers: Collection<OauthProvider>) {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val servicesByName: Map<OauthProviderName, OauthProvider> =
      providers.associateBy { it.providerName }

  fun getService(type: OauthProviderName): OauthProvider? {
    val provider = servicesByName[type]
    if (provider == null) {
      log.debug("OAuth provider lookup failed for type={}.", type)
    } else {
      log.trace("OAuth provider resolved by enum type={}.", type)
    }
    return provider
  }

  fun getService(type: String): OauthProvider? {
    val providerName = parseProviderName(type) ?: return null
    val provider = servicesByName[providerName]
    if (provider == null) {
      log.debug(
          "OAuth provider lookup failed for rawType={}, normalizedType={}.", type, providerName)
    } else {
      log.trace("OAuth provider resolved by rawType={}, normalizedType={}.", type, providerName)
    }
    return provider
  }

  fun requireService(type: OauthProviderName): OauthProvider {
    return servicesByName[type]
        ?: run {
          log.info("OAuth provider is not registered for required type={}.", type)
          throw OauthException("OAuth provider is not registered: ${type.name}")
        }
  }

  private fun parseProviderName(value: String): OauthProviderName? {
    return runCatching { OauthProviderName.valueOf(value.uppercase(Locale.ROOT)) }.getOrNull()
  }
}
