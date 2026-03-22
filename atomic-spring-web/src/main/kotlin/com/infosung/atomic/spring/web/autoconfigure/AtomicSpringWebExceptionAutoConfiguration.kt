package com.infosung.atomic.spring.web.autoconfigure

import com.infosung.atomic.spring.web.exception.AtomicHttpExceptionHandler
import com.infosung.atomic.spring.web.exception.AtomicHttpExceptionHandlerReplacement
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.RestControllerAdvice

@AutoConfiguration
@ConditionalOnClass(RestControllerAdvice::class)
class AtomicSpringWebExceptionAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(AtomicHttpExceptionHandlerReplacement::class)
  fun atomicHttpExceptionHandler(environment: Environment): AtomicHttpExceptionHandler {
    return AtomicHttpExceptionHandler(environment = environment)
  }
}
