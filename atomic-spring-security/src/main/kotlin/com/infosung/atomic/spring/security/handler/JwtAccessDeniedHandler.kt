package com.infosung.atomic.spring.security.handler

import com.infosung.atomic.contract.response.BaseResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import tools.jackson.databind.ObjectMapper

class JwtAccessDeniedHandler(
    val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
  private val log: Logger = LoggerFactory.getLogger(JwtAccessDeniedHandler::class.java)

  override fun handle(
      request: HttpServletRequest,
      response: HttpServletResponse,
      accessDeniedException: AccessDeniedException,
  ) {
    log.warn(
        "Access denied: method={}, uri={}",
        request.method,
        request.requestURI,
        accessDeniedException,
    )
    response.status = HttpServletResponse.SC_FORBIDDEN
    response.writer?.print(
        objectMapper.writeValueAsString(
            BaseResponse<Any>(
                code = HttpStatus.FORBIDDEN.name,
                message = HttpStatus.FORBIDDEN.name,
            ),
        ),
    )
    response.contentType = MediaType.APPLICATION_JSON.toString()
  }
}
