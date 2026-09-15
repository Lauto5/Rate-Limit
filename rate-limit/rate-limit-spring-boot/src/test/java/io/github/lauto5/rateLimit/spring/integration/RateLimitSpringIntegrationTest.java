package io.github.lauto5.rateLimit.spring.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.spring.autoconfigure.RateLimitAutoConfiguration;

/**
 * End-to-end wiring: Spring provisions the persistence adapter and the consumer builds its own
 * strongly typed {@link RateLimit} through the core API.
 */
class RateLimitSpringIntegrationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class))
			.withUserConfiguration(ApplicationConfiguration.class);

	@Test
	void storeAndClockAreInjectedAndRateLimitEnforcesThePolicy() {
		contextRunner.run(context -> {
			RateLimit<FixedWindowPolicy> rateLimit = context.getBean(RateLimit.class);

			FixedWindowPolicy policy = new FixedWindowPolicy(1, Duration.ofMinutes(1));

			RateLimitResult first = rateLimit.use("user-1", policy);
			assertTrue(first.isAllowed());

			RateLimitResult second = rateLimit.use("user-1", policy);
			assertFalse(second.isAllowed());
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class ApplicationConfiguration {

		@Bean
		RateLimit<FixedWindowPolicy> rateLimit(RateLimitStore store) {
			return RateLimit.build(Algorithm.fixedWindow(), store);
		}
	}
}