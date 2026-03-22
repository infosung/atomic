package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.spring.web.autoconfigure.AtomicSpringWebExceptionAutoConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

class AtomicSpringWebExceptionAutoConfigurationTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AtomicSpringWebExceptionAutoConfiguration::class.java))

  @Test
  fun `default atomic http exception handler should register when no custom base handler exists`() {
    contextRunner.run { context ->
      assertNotNull(context.getBean(AtomicHttpExceptionHandler::class.java))
      assertEquals(1, context.getBeanNamesForType(BaseExceptionHandler::class.java).size)
    }
  }

  @Test
  fun `custom base exception handler alone should not suppress default atomic http exception handler`() {
    contextRunner
        .withBean(
            BaseExceptionHandler::class.java,
            { CustomExceptionHandler(MockEnvironment()) },
        )
        .run { context ->
          assertTrue(context.containsBean("atomicHttpExceptionHandler"))
          assertEquals(2, context.getBeanNamesForType(BaseExceptionHandler::class.java).size)
        }
  }

  @Test
  fun `custom replacement handler should suppress default atomic http exception handler`() {
    contextRunner
        .withBean(
            ReplacingExceptionHandler::class.java,
            { ReplacingExceptionHandler(MockEnvironment()) },
        )
        .run { context ->
          assertFalse(context.containsBean("atomicHttpExceptionHandler"))
          assertEquals(1, context.getBeanNamesForType(BaseExceptionHandler::class.java).size)
          assertEquals(
              1,
              context.getBeanNamesForType(AtomicHttpExceptionHandlerReplacement::class.java).size,
          )
          val handler = context.getBean(BaseExceptionHandler::class.java)
          assertEquals(ReplacingExceptionHandler::class.java.name, handler.javaClass.name)
        }
  }
}

class BaseExceptionHandlerCustomizationSmokeTest {
  @Test
  fun `documented base exception handler subclass should handle coded http status exceptions`() {
    val mockMvc =
        MockMvcBuilders.standaloneSetup(ThrowingController())
            .setControllerAdvice(CustomExceptionHandler(MockEnvironment()))
            .build()

    mockMvc
        .perform(get("/test/error"))
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.code").value("DOC_SAMPLE_CODE"))
        .andExpect(jsonPath("$.message").value("Doc sample failure"))
  }

  @Test
  fun `documented base exception handler subclass should allow centralized response customization`() {
    val mockMvc =
        MockMvcBuilders.standaloneSetup(ThrowingController())
            .setControllerAdvice(CustomizingExceptionHandler(MockEnvironment()))
            .build()

    mockMvc
        .perform(get("/test/error"))
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.code").value("HOST_DOC_OVERRIDE"))
        .andExpect(jsonPath("$.message").value("Host customized response"))
  }
}

@RestController
private class ThrowingController {
  @GetMapping("/test/error")
  fun error(): String {
    throw HttpStatusException(
        status = 400,
        code = "DOC_SAMPLE_CODE",
        message = "Doc sample failure",
    )
  }
}

@RestControllerAdvice
private class CustomExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment) {
  override fun alert(
      e: Exception,
      message: String,
  ) = Unit
}

@RestControllerAdvice
private class CustomizingExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment) {
  override fun createErrorResponse(
      e: Exception,
      status: Int,
  ): BaseResponse<Any> {
    return BaseResponse(
        code = "HOST_DOC_OVERRIDE",
        message = "Host customized response",
    )
  }

  override fun alert(
      e: Exception,
      message: String,
  ) = Unit
}

@RestControllerAdvice
private class ReplacingExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment), AtomicHttpExceptionHandlerReplacement {
  override fun alert(
      e: Exception,
      message: String,
  ) = Unit
}
