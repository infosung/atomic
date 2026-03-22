package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.spring.web.autoconfigure.AtomicSpringWebExceptionAutoConfiguration
import jakarta.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

class BaseExceptionHandlerDocumentationContractTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AtomicSpringWebExceptionAutoConfiguration::class.java))

  @Test
  fun `documented app exception handler example should keep built in scoped handler`() {
    contextRunner
        .withBean(
            AppExceptionHandler::class.java,
            { AppExceptionHandler(MockEnvironment()) },
        )
        .run { context ->
          assertTrue(context.containsBean("atomicHttpExceptionHandler"))
          assertEquals(2, context.getBeanNamesForType(BaseExceptionHandler::class.java).size)
          assertEquals(
              0,
              context.getBeanNamesForType(AtomicHttpExceptionHandlerReplacement::class.java).size)
        }
  }

  @Test
  fun `documented global replacement example should suppress built in scoped handler`() {
    contextRunner
        .withBean(
            GlobalAppExceptionHandler::class.java,
            { GlobalAppExceptionHandler(MockEnvironment()) },
        )
        .run { context ->
          assertFalse(context.containsBean("atomicHttpExceptionHandler"))
          assertEquals(1, context.getBeanNamesForType(BaseExceptionHandler::class.java).size)
          assertEquals(
              1,
              context.getBeanNamesForType(AtomicHttpExceptionHandlerReplacement::class.java).size,
          )
          val handler = context.getBean(BaseExceptionHandler::class.java)
          assertEquals(GlobalAppExceptionHandler::class.java.name, handler.javaClass.name)
        }
  }

  @Test
  fun `documented app exception handler example should alert only for 5xx`() {
    val handler = AppExceptionHandler(MockEnvironment())
    val request = MockHttpServletRequest("GET", "/docs/error")

    val clientResponse =
        handler.httpStatusException(
            HttpStatusException(
                status = 400,
                code = "DOC_SAMPLE_BAD_REQUEST",
                message = "Bad request",
            ),
            request,
        )

    assertEquals(400, clientResponse.statusCode.value())
    assertEquals(0, handler.alertCalls)

    val serverResponse = handler.exception(IllegalStateException("boom"), request)

    assertEquals(500, serverResponse.statusCode.value())
    assertEquals(1, handler.alertCalls)
    assertTrue(handler.lastAlertMessage?.contains("GET /docs/error") == true)
  }

  @Test
  fun `documented create error response example should customize the envelope`() {
    val mockMvc =
        MockMvcBuilders.standaloneSetup(DocumentationThrowingController())
            .setControllerAdvice(ResponseCustomizingAppExceptionHandler(MockEnvironment()))
            .build()

    mockMvc
        .perform(get("/docs/error"))
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.code").value("HOST_OVERRIDE_CODE"))
        .andExpect(jsonPath("$.message").value("Host override response"))
  }
}

@RestController
private class DocumentationThrowingController {
  @GetMapping("/docs/error")
  fun error(): String {
    throw HttpStatusException(
        status = 400,
        code = "DOC_SAMPLE_CODE",
        message = "Doc sample failure",
    )
  }
}

/** Matches the documented plain host advice example in `atomic-spring-web.md`. */
@RestControllerAdvice
private class AppExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment) {
  var alertCalls: Int = 0
  var lastAlertMessage: String? = null

  override fun shouldAlert(
      e: Exception,
      request: HttpServletRequest,
      status: Int,
  ): Boolean = status >= 500

  override fun alert(
      e: Exception,
      message: String,
  ) {
    alertCalls += 1
    lastAlertMessage = message
  }
}

/** Matches the documented replacement example in `atomic-spring-web.md`. */
@RestControllerAdvice
private class GlobalAppExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment), AtomicHttpExceptionHandlerReplacement {
  override fun shouldAlert(
      e: Exception,
      request: HttpServletRequest,
      status: Int,
  ): Boolean = status >= 500

  override fun alert(
      e: Exception,
      message: String,
  ) = Unit
}

/** Matches the documented response customization example in `atomic-spring-web.md`. */
@RestControllerAdvice
private class ResponseCustomizingAppExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment) {
  override fun createErrorResponse(
      e: Exception,
      status: Int,
  ): BaseResponse<Any> {
    return BaseResponse(
        code = "HOST_OVERRIDE_CODE",
        message = "Host override response",
    )
  }

  override fun alert(
      e: Exception,
      message: String,
  ) = Unit
}
