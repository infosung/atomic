package com.infosung.atomic.heartbeat

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** HTTP ping provider with per-event endpoint routing. */
class HttpHeartbeatProvider(
    private val successUrl: String,
    private val failUrl: String = successUrl,
    private val startUrl: String? = null,
    private val connectTimeout: Duration = Duration.ofSeconds(1),
    private val requestTimeout: Duration = Duration.ofSeconds(2),
    private val headers: Map<String, String> = emptyMap(),
) : HeartbeatProvider {
  private val client: HttpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build()

  override fun send(event: HeartbeatEvent) {
    val targetUrl =
        when (event.type) {
          HeartbeatEventType.START -> startUrl ?: successUrl
          HeartbeatEventType.SUCCESS -> successUrl
          HeartbeatEventType.FAIL -> failUrl
        }

    val requestBuilder =
        HttpRequest.newBuilder().uri(URI.create(targetUrl)).GET().timeout(requestTimeout)
    headers.forEach { (name, value) -> requestBuilder.header(name, value) }
    val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
    if (response.statusCode() >= 400) {
      throw IllegalStateException(
          "Heartbeat ping failed with HTTP ${response.statusCode()}: $targetUrl")
    }
  }
}
