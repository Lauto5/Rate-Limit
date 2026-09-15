package io.github.lauto5.rateLimit.spring.autoconfigure;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.infrastructure.InMemoryStore;
import io.github.lauto5.rateLimit.infrastructure.RedisStore;
import io.github.lauto5.rateLimit.spring.properties.RateLimitProperties;
import io.github.lauto5.rateLimit.spring.properties.RedisProperties;

/**
 * Spring Boot auto-configuration for Rate Limit.
 *
 * <p>Spring provisions the infrastructure only: a {@link RateLimitStore} persisted in-memory or
 * in Redis (according to {@code rate-limit.persistence.type}) and a {@link Clock}. The consumer
 * keeps using the core API directly, building its own {@link RateLimit} with
 * {@link RateLimit#build} and the injected store.
 *
 * <p>The integration never creates a {@link RateLimit} bean: the algorithm and the policy are a
 * core concern, selected by the consumer to preserve the strongly typed API.
 *
 * <p>Activated when {@code rate-limit.enabled=true} (default) and {@code RateLimit} is on the
 * classpath. Redis support is active only when the Redis module is present and
 * {@code rate-limit.persistence.type=redis}. Every bean is {@link ConditionalOnMissingBean}, so
 * a consumer-defined bean always wins.
 */
@AutoConfiguration
@ConditionalOnClass(RateLimit.class)
@ConditionalOnProperty(prefix = "rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

	private static final String PERSISTENCE_PREFIX = "rate-limit.persistence";

	@Bean
	@ConditionalOnMissingBean(Clock.class)
	public Clock rateLimitClock() {
		return Clock.systemUTC();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(InMemoryStore.class)
	@ConditionalOnProperty(prefix = PERSISTENCE_PREFIX, name = "type", havingValue = "inmemory",
			matchIfMissing = true)
	static class InMemoryRateLimitConfiguration {

		@Bean
		@ConditionalOnMissingBean(RateLimitStore.class)
		public RateLimitStore inMemoryRateLimitStore() {
			return io.github.lauto5.rateLimit.inmemory.Persistence.inMemory();
		}
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(RedisStore.class)
	@ConditionalOnProperty(prefix = PERSISTENCE_PREFIX, name = "type", havingValue = "redis")
	static class RedisRateLimitConfiguration {

		@Bean(destroyMethod = "close")
		@ConditionalOnMissingBean(RateLimitStore.class)
		public RateLimitStore redisRateLimitStore(RateLimitProperties rateLimitProperties) {
			RedisProperties redisProperties = rateLimitProperties.getPersistence().getRedis();
			return io.github.lauto5.rateLimit.redis.Persistence.inRedis(
					redisProperties.getUrl(),
					redisProperties.getNamespace()
			);
		}
	}
}