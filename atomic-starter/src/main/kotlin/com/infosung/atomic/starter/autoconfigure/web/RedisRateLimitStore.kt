package com.infosung.atomic.starter.autoconfigure.web

import com.infosung.atomic.spring.web.ratelimit.RateLimitDecision
import com.infosung.atomic.spring.web.ratelimit.RateLimitPolicy
import com.infosung.atomic.spring.web.ratelimit.RateLimitStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

/** Redis-backed [RateLimitStore] using atomic INCR/TTL script. */
class RedisRateLimitStore(
    private val redisTemplate: StringRedisTemplate,
    private val keyPrefix: String,
) : RateLimitStore {
  override fun consume(
      key: String,
      policy: RateLimitPolicy,
      nowMillis: Long,
  ): RateLimitDecision {
    val window = calculateFixedWindowRateLimitState(nowMillis, policy.windowSeconds)
    val windowStart = window.windowStartSeconds
    val redisKey = "$keyPrefix$key|${policy.limit}|${policy.windowSeconds}|$windowStart"
    val resetAfterSeconds = window.resetAfterSeconds
    val expireSeconds = window.expireSeconds

    val result =
        redisTemplate.execute(
            SCRIPT,
            listOf(redisKey),
            policy.limit.toString(),
            expireSeconds.toString(),
        ) ?: throw IllegalStateException("Redis rate-limit script returned null.")

    val allowed = (result[0] as Number).toLong() == 1L
    val remaining = (result[1] as Number).toLong().coerceAtLeast(0)
    return RateLimitDecision(
        allowed = allowed,
        limit = policy.limit,
        remaining = remaining,
        retryAfterSeconds = if (allowed) null else resetAfterSeconds.coerceAtLeast(1),
        resetAfterSeconds = resetAfterSeconds,
    )
  }

  companion object {
    private val SCRIPT =
        DefaultRedisScript(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            local limit = tonumber(ARGV[1])
            local remaining = limit - current
            if remaining < 0 then remaining = 0 end
            local allowed = 0
            if current <= limit then allowed = 1 end
            return {allowed, remaining}
          """
                .trimIndent(),
            List::class.java,
        )
  }
}
