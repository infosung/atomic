package com.infosung.atomic.spring.security.handler

import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.spring.security.SecurityErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import tools.jackson.databind.ObjectMapper

/** Spring Security access-denied handler returning JSON 403 response body. */
class JwtAccessDeniedHandler(
    val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
  private val log: Logger = LoggerFactory.getLogger(JwtAccessDeniedHandler::class.java)

  /** Writes `403 Forbidden` response with [BaseResponse] payload. */
  override fun handle(
      request: HttpServletRequest,
      response: HttpServletResponse,
      accessDeniedException: AccessDeniedException,
  ) {
    val errorCode = SecurityErrorCode.SECURITY_FORBIDDEN
    log.warn(
        "Access denied: method={}, uri={}",
        request.method,
        request.requestURI,
        accessDeniedException,
    )
    response.status = errorCode.defaultHttpStatus
    response.writer?.print(
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
