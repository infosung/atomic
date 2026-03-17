package com.infosung.atomic.app.oauth

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class AllowedRedirectUriPolicyTest {
  @Test
  fun `validateRedirectUri should allow custom scheme deep link with host`() {
    val redirectUri =
        AllowedRedirectUriPolicy.validateRedirectUri(
            redirectUri = "myapp://oauth/callback",
            configuredPrefixes = listOf("myapp://oauth"),
        )

    assertEquals("myapp://oauth/callback", redirectUri)
  }

  @Test
  fun `validateRedirectUri should allow hostless custom scheme deep link`() {
    val redirectUri =
        AllowedRedirectUriPolicy.validateRedirectUri(
            redirectUri = "myapp:/oauth/callback",
            configuredPrefixes = listOf("myapp:/oauth"),
        )

    assertEquals("myapp:/oauth/callback", redirectUri)
  }

  @Test
  fun `validateRedirectUri should log matched allowlist pattern for accepted redirect`() {
    val logger = LoggerFactory.getLogger(AllowedRedirectUriPolicy::class.java) as Logger
    val originalLevel = logger.level
    logger.level = Level.DEBUG
    val appender = ListAppender<ILoggingEvent>()
    appender.start()
    logger.addAppender(appender)

    try {
      AllowedRedirectUriPolicy.validateRedirectUri(
          redirectUri = "myapp://oauth/callback",
          configuredPrefixes = listOf("myapp://oauth"),
      )
    } finally {
      logger.detachAppender(appender)
      logger.level = originalLevel
    }

    assertTrue(
        appender.list.any {
          it.formattedMessage.contains("Accepted oauth redirect URI") &&
              it.formattedMessage.contains("myapp://oauth") &&
              it.formattedMessage.contains("myapp://oauth/callback")
        },
    )
  }
}
