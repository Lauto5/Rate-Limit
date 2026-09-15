package io.github.lauto5.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.infrastructure.InMemoryStore;

/**
 * Consumer validation (InMemory): the auto-configuration provisions the correct store and the
 * core domain API is usable directly, without casts or Spring-specific abstractions.
 */
@SpringBootTest(classes = SpringBootInMemoryApplication.class)
class SpringBootInMemoryConsumerTest {

	private static final FixedWindowPolicy POLICY = new FixedWindowPolicy(1, Duration.ofMinutes(1));

	@Autowired
	private RateLimitStore store;

	@Autowired
	private Clock clock;

	@Autowired
	private RateLimit<FixedWindowPolicy> rateLimit;

	@Test
	void autoConfigurationProvisionsInMemoryStoreAndClock() {
		assertInstanceOf(InMemoryStore.class, store);
		assertInstanceOf(Clock.class, clock);
	}

	@Test
	void domainApiIsUsableWithoutCasts() {
		RateLimitResult first = rateLimit.use("consumer-1", POLICY);
		assertTrue(first.isAllowed());

		RateLimitResult second = rateLimit.use("consumer-1", POLICY);
		assertFalse(second.isAllowed());
	}
}