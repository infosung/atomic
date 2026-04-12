package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeEntity
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@AutoConfiguration(
    afterName = ["org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"])
@ConditionalOnClass(
    name =
        [
            "org.springframework.data.jpa.repository.JpaRepository",
            "jakarta.persistence.Entity",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.app.oauth.redirect.store",
    name = ["type"],
    havingValue = "entity",
)
@ConditionalOnProperty(
    prefix = "atomic.app.oauth.redirect",
    name = ["enabled"],
    havingValue = "true",
)
@EntityScan(basePackageClasses = [OauthRelayCodeEntity::class])
@EnableJpaRepositories(basePackageClasses = [OauthRelayCodeRepository::class])
internal class AtomicAppOauthRedirectPersistenceAutoConfiguration
