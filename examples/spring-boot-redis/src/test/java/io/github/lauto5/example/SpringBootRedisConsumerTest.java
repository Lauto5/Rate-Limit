package io.github.lauto5.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.infrastructure.RedisStore;

/**
 * Consumer validation (Redis): the auto-configuration provisions a real Redis-backed store
 * (Testcontainers), the domain API works end-to-end, and the store lifecycle is wired to
 * Spring's shutdown via {@code destroyMethod="close"}.
 */
@Testcontainers
@SpringBootTest(classes = SpringBootRedisApplication.class)
class SpringBootRedisConsumerTest {

	private static final FixedWindowPolicy POLICY = new FixedWindowPolicy(1, Duration.ofMinutes(1));

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisUrl(DynamicPropertyRegistry registry) {
		registry.add("rate-limit.persistence.redis.url",
				() -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
	}

	@Autowired
	private RateLimitStore store;

	@Autowired
	private RateLimit<FixedWindowPolicy> rateLimit;

	@Autowired
	private ConfigurableApplicationContext context;

	@Test
	void autoConfigurationProvisionsRedisStore() {
		assertInstanceOf(RedisStore.class, store);
	}

	@Test
	void domainApiWorksAgainstRedisEndToEnd() {
		RateLimitResult first = rateLimit.use("consumer-1", POLICY);
		assertTrue(first.isAllowed());

		RateLimitResult second = rateLimit.use("consumer-1", POLICY);
		assertFalse(second.isAllowed());
	}

	@Test
	void redisStoreDeclaresCloseAsDestroyMethod() {
		RootBeanDefinition definition = (RootBeanDefinition) ((org.springframework.beans.factory.support.DefaultListableBeanFactory) context
				.getBeanFactory())
				.getMergedBeanDefinition("redisRateLimitStore");
		assertEquals("close", definition.getDestroyMethodName());
	}
}