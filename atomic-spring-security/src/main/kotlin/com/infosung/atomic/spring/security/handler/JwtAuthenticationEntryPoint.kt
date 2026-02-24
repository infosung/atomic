package com.infosung.atomic.spring.security.handler

import com.infosung.atomic.contract.response.BaseResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.ObjectMapper

class JwtAuthenticationEntryPoint(
    val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
  private val log: Logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint::class.java)

  override fun commence(
      request: HttpServletRequest,
      response: HttpServletResponse,
      authException: AuthenticationException,
  ) {
    log.warn(
        "Authentication entry point triggered: method={}, uri={}",
        request.method,
        request.requestURI,
        authException,
    )
    response.status = HttpServletResponse.SC_UNAUTHORIZED
    response.writer.print(
        objectMapper.writeValueAsString(
            BaseResponse<Any>(
                code = HttpStatus.UNAUTHORIZED.name,
                message = HttpStatus.UNAUTHORIZED.name,
            ),
        ),
    )
    response.contentType = MediaType.APPLICATION_JSON.toString()
  }
}
