package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.app.storage.adapter.out.persistence.AppImageEntityTxService
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageEntity
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageRepository
import jakarta.persistence.EntityManagerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfiguration
@ConditionalOnBean(value = [EntityManagerFactory::class, JdbcTemplate::class])
@EntityScan(basePackageClasses = [ImageEntity::class])
@EnableJpaRepositories(basePackageClasses = [ImageRepository::class])
class AtomicAppImagePersistenceAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun appImageEntityTxService(imageRepository: ImageRepository): AppImageEntityTxService {
    return AppImageEntityTxService(imageRepository = imageRepository)
  }

  @Bean
  @ConditionalOnMissingBean
  fun appImageSchemaUpgradePreflight(
      jdbcTemplate: JdbcTemplate,
  ): AppImageSchemaUpgradePreflight {
    return AppImageSchemaUpgradePreflight(jdbcTemplate = jdbcTemplate)
  }
}
