package io.github.lauto5.example;

import java.time.Duration;

import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.logging.ConsoleLogger;
import io.github.lauto5.rateLimit.application.ports.out.Logger;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.inmemory.Persistence;

/**
 * Example of using the library with in-memory persistence.
 *
 * <p>Shows the minimal combination of dependencies:
 *
 * <pre>
 * rate-limit-core
 * rate-limit-inmemory
 * </pre>
 *
 * <p>To run it:
 *
 * <pre>
 * mvn -f examples/core-inmemory compile exec:java
 * </pre>
 */
public final class InMemoryExample {

	private InMemoryExample() {
		// utility
	}

	public static void main(String[] args) {

		Logger logger = new ConsoleLogger("inmemory-example", Logger.Level.DEBUG);
		RateLimitStore store = Persistence.inMemory(logger);

		RateLimit<FixedWindowPolicy> rateLimit =
				RateLimit.build(new FixedWindowAlgorithmImpl(), store, logger);

		FixedWindowPolicy policy = new FixedWindowPolicy(3, Duration.ofMinutes(1));

		System.out.println("Limite: 3 requests por minuto por identifier");
		System.out.println("----------------------------------------------");

		for (int i = 1; i <= 5; i++) {

			RateLimitResult result = rateLimit.use("user-42", policy);

			System.out.println("Request " + i + " -> "
					+ (result.isAllowed() ? "ALLOWED" : "DENIED")
					+ " (remaining=" + result.getRemaining() + ")");

			if (!result.isAllowed()) {
				System.out.println("          retry after: "
						+ result.getRetryAfter().map(Duration::toMillis).orElse(-1L) + " ms");
			}
		}
	}
}