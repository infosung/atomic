package com.infosung.atomic.spring.web.exception

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Shared default Spring MVC exception handler for Atomic modules.
 *
 * Hosts can keep using a higher-precedence advice, but Atomic app modules no longer need to ship
 * dedicated `*HttpExceptionHandler` beans.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(basePackages = ["com.infosung.atomic.app"])
class AtomicHttpExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment) {
  override fun shouldAlert(
      e: Exception,
      request: HttpServletRequest,
      status: Int,
  ): Boolean = false

  override fun alert(
      e: Exception,
      message: String,
  ) {
    // Default shared handler intentionally does not send alerts.
  }
}
