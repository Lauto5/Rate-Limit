package io.github.lauto5.example;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

/**
 * Example of using the library inside a Spring Boot application with in-memory persistence.
 *
 * <p>Shows the combination of dependencies:
 *
 * <pre>
 * rate-limit-core
 * rate-limit-inmemory
 * rate-limit-spring-boot
 * </pre>
 *
 * <p>Spring provisions the {@link RateLimitStore}; the application builds its own strongly typed
 * {@link RateLimit} bean. To run it:
 *
 * <pre>
 * mvn -f examples/spring-boot-inmemory compile exec:java
 * </pre>
 */
@SpringBootApplication
public class SpringBootInMemoryApplication {

	public static void main(String[] args) {
		try (ConfigurableApplicationContext context = SpringApplication.run(SpringBootInMemoryApplication.class, args)) {

			RateLimit<FixedWindowPolicy> rateLimit = context.getBean(RateLimit.class);
			FixedWindowPolicy policy = new FixedWindowPolicy(3, Duration.ofMinutes(1));

			System.out.println("Spring Boot + inmemory - Limite: 3 requests por minuto por identifier");
			System.out.println("----------------------------------------------------------------------");

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

	@Bean
	RateLimit<FixedWindowPolicy> rateLimit(RateLimitStore store) {
		return RateLimit.build(Algorithm.fixedWindow(), store);
	}
}