package io.github.lauto5.example;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.infrastructure.InMemoryStore;

/**
 * Consumer validation (InMemory): beans defined by the consumer always win over the
 * auto-configured defaults (`@ConditionalOnMissingBean`).
 */
@SpringBootTest(classes = { SpringBootInMemoryApplication.class, SpringBootInMemoryOverrideTest.OverrideConfiguration.class })
class SpringBootInMemoryOverrideTest {

	@Autowired
	private RateLimitStore store;

	@Autowired
	private Clock clock;

	@Test
	void consumerDefinedStoreAndClockWin() {
		assertSame(OverrideConfiguration.STORE, store);
		assertSame(OverrideConfiguration.CLOCK, clock);
	}

	@Configuration(proxyBeanMethods = false)
	static class OverrideConfiguration {

		static final RateLimitStore STORE = new InMemoryStore();

		static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC);

		@Bean
		RateLimitStore store() {
			return STORE;
		}

		@Bean
		Clock clock() {
			return CLOCK;
		}
	}
}