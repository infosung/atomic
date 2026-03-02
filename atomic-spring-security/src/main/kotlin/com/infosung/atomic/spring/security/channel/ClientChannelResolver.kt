package com.infosung.atomic.spring.security.channel

import jakarta.servlet.http.HttpServletRequest
import java.net.URI
import java.util.Locale
import org.slf4j.LoggerFactory

/** Logical client channel used to select token resolution strategy. */
enum class ClientChannel {
  WEB,
  APP,
  UNKNOWN,
}

/** Resolves request channel from servlet request context. */
fun interface ClientChannelResolver {
  /** @return Resolved request channel. */
  fun resolve(request: HttpServletRequest): ClientChannel
}

/** Default resolver returning [ClientChannel.UNKNOWN]. */
class DefaultClientChannelResolver : ClientChannelResolver {
  override fun resolve(request: HttpServletRequest): ClientChannel = ClientChannel.UNKNOWN
}

/** Resolves channel by comparing `Host`/`Origin`/`Referer` against configured domains. */
class HostBasedClientChannelResolver(
    webDomains: List<String>,
    apiDomains: List<String>,
) : ClientChannelResolver {
  private val log = LoggerFactory.getLogger(HostBasedClientChannelResolver::class.java)
  private val webHosts = webDomains.mapNotNull(::normalizeHost).toSet()
  private val apiHosts = apiDomains.mapNotNull(::normalizeHost).toSet()

  init {
    log.info(
        "HostBasedClientChannelResolver initialized: webHosts={}, apiHosts={}",
        webHosts.size,
        apiHosts.size,
    )
    log.trace("Configured webHosts={}, apiHosts={}", webHosts, apiHosts)
  }

  override fun resolve(request: HttpServletRequest): ClientChannel {
    val requestHost = requestHost(request)
    if (apiHosts.isNotEmpty() && requestHost != null && requestHost !in apiHosts) {
      log.debug(
          "Channel resolution result=UNKNOWN (request host not in apiHosts): requestHost={}",
          requestHost,
      )
      return ClientChannel.UNKNOWN
    }

    val originHost = normalizeHost(request.getHeader("Origin"))
    if (originHost != null) {
      val channel = if (originHost in webHosts) ClientChannel.WEB else ClientChannel.UNKNOWN
      log.debug(
          "Channel resolution by Origin: originHost={}, requestHost={}, result={}",
          originHost,
          requestHost,
          channel,
      )
      return channel
    }

    val refererHost = normalizeHost(request.getHeader("Referer"))
    if (refererHost != null) {
      val channel = if (refererHost in webHosts) ClientChannel.WEB else ClientChannel.UNKNOWN
      log.debug(
          "Channel resolution by Referer: refererHost={}, requestHost={}, result={}",
          refererHost,
          requestHost,
          channel,
      )
      return channel
    }

    log.debug("Channel resolution fallback result=APP: requestHost={}", requestHost)
    return ClientChannel.APP
  }

  private fun requestHost(request: HttpServletRequest): String? {
    val hostHeader = normalizeHost(request.getHeader("Host"))
    if (hostHeader != null) return hostHeader

    val serverName = request.serverName?.trim().orEmpty()
    if (serverName.isBlank()) return null
    val port = request.serverPort
    val scheme = request.scheme?.lowercase(Locale.ROOT)
    val withPort =
        when {
          port <= 0 -> serverName
          scheme == "http" && port == 80 -> serverName
          scheme == "https" && port == 443 -> serverName
          else -> "$serverName:$port"
        }
    return normalizeHost(withPort)
  }

  private fun normalizeHost(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val input = raw.trim()

    val fromUri = tryParseHostPort(input)
    if (fromUri != null) return fromUri.lowercase(Locale.ROOT)

    val withoutPath = input.substringBefore('/').substringBefore('?').substringBefore('#')
    if (withoutPath.isBlank()) return null
    return withoutPath.removePrefix("//").lowercase(Locale.ROOT)
  }

  private fun tryParseHostPort(value: String): String? {
    val candidates = buildList {
      add(value)
      if (!value.startsWith("http://", ignoreCase = true) &&
          !value.startsWith("https://", ignoreCase = true)) {
        add("http://$value")
      }
    }

    for (candidate in candidates) {
      try {
        val uri = URI(candidate)
        val host = uri.host ?: continue
        val port = uri.port
        return if (port > 0) "$host:$port" else host
      } catch (_: Exception) {
        // ignore parse errors and try next candidate
      }
    }
    return null
  }
}
