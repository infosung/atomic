package com.infosung.atomic.starter.autoconfigure.contract

import com.infosung.atomic.contract.header.TraceIdGenerator
import com.infosung.atomic.contract.time.TimeProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/** Auto-configuration for base contract utilities. */
@AutoConfiguration
@ConditionalOnClass(
    name =
        [
            "com.infosung.atomic.contract.time.TimeProvider",
            "com.infosung.atomic.contract.header.TraceIdGenerator",
        ],
)
class AtomicContractAutoConfiguration {
  /** Registers default [TimeProvider] when absent. */
  @Bean @ConditionalOnMissingBean fun timeProvider(): TimeProvider = TimeProvider()

  /** Registers default [TraceIdGenerator] when absent. */
  @Bean @ConditionalOnMissingBean fun traceIdGenerator(): TraceIdGenerator = TraceIdGenerator()
}
