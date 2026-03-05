package com.infosung.atomic.heartbeat

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpHeartbeatProviderTest {
  @Test
  fun `http error should not leak target url in exception message`() {
    val token = "secret-token-123"
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val path = "/ping/$token/fail"
    server.createContext(path) { exchange ->
      exchange.sendResponseHeaders(500, -1)
      exchange.close()
    }
    server.start()

    try {
      val targetUrl = "http://127.0.0.1:${server.address.port}$path?key=$token"
      val provider = HttpHeartbeatProvider(successUrl = targetUrl, failUrl = targetUrl)

      val error =
          assertFailsWith<IllegalStateException> {
            provider.send(HeartbeatEvent(type = HeartbeatEventType.FAIL))
          }

      val message = error.message ?: ""
      assertTrue(message.contains("HTTP 500"), message)
      assertTrue(message.contains(token).not(), message)
      assertTrue(message.contains("http://127.0.0.1").not(), message)
    } finally {
      server.stop(0)
    }
  }
}
