package com.infosung.atomic.spring.security.handler

import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.spring.security.SecurityErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.ObjectMapper

/** Spring Security authentication entry point returning JSON 401 response body. */
class JwtAuthenticationEntryPoint(
    val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
  private val log: Logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint::class.java)

  /** Writes `401 Unauthorized` response with [BaseResponse] payload. */
  override fun commence(
      request: HttpServletRequest,
      response: HttpServletResponse,
      authException: AuthenticationException,
  ) {
    val errorCode = SecurityErrorCode.SECURITY_UNAUTHORIZED
    log.warn(
        "Authentication entry point triggered: method={}, uri={}",
        request.method,
        request.requestURI,
        authException,
    )
    response.status = errorCode.defaultHttpStatus
    response.writer.print(
        objectMapper.writeValueAsString(
            BaseResponse<Any>(
                code = errorCode.name,
                message = errorCode.defaultMessage,
            ),
        ),
    )
    response.contentType = MediaType.APPLICATION_JSON.toString()
  }
}
