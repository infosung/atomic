package com.infosung.atomic.spring.security.config

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.channel.ClientChannelResolver
import com.infosung.atomic.spring.security.channel.DefaultClientChannelResolver
import com.infosung.atomic.spring.security.filter.SecurityFilter
import com.infosung.atomic.spring.security.jwt.JwtProvider
import com.infosung.atomic.spring.security.util.SecurityCookiePolicy
import com.infosung.atomic.spring.security.util.SecurityUtil
import org.slf4j.LoggerFactory
import org.springframework.security.config.annotation.SecurityConfigurerAdapter
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

/**
 * Spring Security configurer that installs [SecurityFilter].
 */
class JwtSecurityConfigurerAdapter(
    private val jwtProvider: JwtProvider,
    private val objectMapper: ObjectMapper,
    private val excludeUrls: List<String>,
    private val clientChannelResolver: ClientChannelResolver = DefaultClientChannelResolver(),
    private val cookiePolicy: SecurityCookiePolicy = SecurityUtil.DEFAULT_COOKIE_POLICY,
    private val timeProvider: TimeProvider = TimeProvider(),
) : SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity>() {
  private val log = LoggerFactory.getLogger(JwtSecurityConfigurerAdapter::class.java)

  /**
   * Adds JWT security filter before [UsernamePasswordAuthenticationFilter].
   */
  override fun configure(httpSecurity: HttpSecurity) {
    log.info("Configuring JWT security filter. excludedUrls={}", excludeUrls.size)
    log.trace("Excluded JWT filter paths={}", excludeUrls)
    val securityFilter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = objectMapper,
            excludeUrls = excludeUrls,
            clientChannelResolver = clientChannelResolver,
            cookiePolicy = cookiePolicy,
            timeProvider = timeProvider,
        )
    httpSecurity.addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter::class.java)
  }
}
