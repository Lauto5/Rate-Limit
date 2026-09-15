package io.github.lauto5.rateLimit.spring.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.infrastructure.InMemoryStore;
import io.github.lauto5.rateLimit.infrastructure.RedisStore;
import io.github.lauto5.rateLimit.spring.properties.PersistenceType;
import io.github.lauto5.rateLimit.spring.properties.RateLimitProperties;

class RateLimitAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

	@Test
	void defaultContextProvisionsInMemoryStoreAndSystemUtcClock() {
		contextRunner.run(context -> {
			assertInstanceOf(InMemoryStore.class, context.getBean(RateLimitStore.class));
			Clock clock = context.getBean(Clock.class);
			assertEquals(Clock.systemUTC().getZone(), clock.getZone());
		});
	}

	@Test
	void redisTypeProvisionsRedisStore() {
		contextRunner
				.withPropertyValues(
						"rate-limit.persistence.type=redis",
						"rate-limit.persistence.redis.url=redis://localhost:6379"
				)
				.run(context -> {
					assertInstanceOf(RedisStore.class, context.getBean(RateLimitStore.class));
				});
	}

	@Test
	void disabledWhenEnabledIsFalse() {
		contextRunner
				.withPropertyValues("rate-limit.enabled=false")
				.run(context -> {
					assertEquals(0, context.getBeansOfType(RateLimitStore.class).size());
					assertEquals(0, context.getBeansOfType(Clock.class).size());
				});
	}

	@Test
	void noStoreWhenInMemoryAdapterIsNotOnClasspath() {
		contextRunner
				.withClassLoader(new FilteredClassLoader(InMemoryStore.class))
				.run(context -> {
					assertEquals(0, context.getBeansOfType(RateLimitStore.class).size());
				});
	}

	@Test
	void noStoreWhenRedisRequestedButAdapterIsNotOnClasspath() {
		contextRunner
				.withClassLoader(new FilteredClassLoader(RedisStore.class))
				.withPropertyValues(
						"rate-limit.persistence.type=redis",
						"rate-limit.persistence.redis.url=redis://localhost:6379"
				)
				.run(context -> {
					assertEquals(0, context.getBeansOfType(RateLimitStore.class).size());
				});
	}

	@Test
	void userDefinedStoreOverridesAutoConfiguredOne() {
		contextRunner
				.withUserConfiguration(UserStoreConfiguration.class)
				.run(context -> {
					RateLimitStore[] stores = context.getBeansOfType(RateLimitStore.class).values().toArray(new RateLimitStore[0]);
					assertEquals(1, stores.length);
					assertInstanceOf(InMemoryStore.class, stores[0]);
					assertSame(UserStoreConfiguration.store, stores[0]);
				});
	}

	@Test
	void userDefinedClockOverridesAutoConfiguredOne() {
		contextRunner
				.withUserConfiguration(UserClockConfiguration.class)
				.run(context -> {
					assertSame(UserClockConfiguration.clock, context.getBean(Clock.class));
				});
	}

	@Test
	void propertiesAreBoundFromEnvironment() {
		contextRunner
				.withPropertyValues(
						"rate-limit.persistence.type=redis",
						"rate-limit.persistence.redis.url=redis://cache.example.com:6380",
						"rate-limit.persistence.redis.namespace=shared-app"
				)
				.run(context -> {
					RateLimitProperties properties = context.getBean(RateLimitProperties.class);
					assertEquals(PersistenceType.REDIS, properties.getPersistence().getType());
					assertEquals("redis://cache.example.com:6380", properties.getPersistence().getRedis().getUrl());
					assertEquals("shared-app", properties.getPersistence().getRedis().getNamespace());
				});
	}

	@Configuration(proxyBeanMethods = false)
	static class UserStoreConfiguration {

		static final RateLimitStore store = new InMemoryStore();

		@Bean
		RateLimitStore userStore() {
			return store;
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class UserClockConfiguration {

		static final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC);

		@Bean
		Clock userClock() {
			return clock;
		}
	}
}