package io.github.lauto5.rateLimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.inmemory.Persistence;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.Logger;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.testdoubles.RecordingLogger;

class LoggerPropagationTest {

	@Test
	void buildWithLoggerShouldEmitLogEntriesThroughPipeline() {

		// Arrange

		RecordingLogger logger = new RecordingLogger();

		RateLimit<FixedWindowPolicy> rateLimit =
				RateLimit.build(
						Algorithm.fixedWindow(),
						Persistence.inMemory(logger),
						logger
				);

		FixedWindowPolicy policy =
				new FixedWindowPolicy(
						2,
						java.time.Duration.ofMinutes(1)
				);

		// Act

		RateLimitResult allowed = rateLimit.use("user-1", policy);
		RateLimitResult denied = rateLimit.use("user-1", policy);
		rateLimit.use("user-1", policy);

		// Assert

		List<RecordingLogger.Entry> entries = logger.getEntries();

		assertTrue(entries.size() >= 3, "expected per-request logs");

		assertTrue(
				logger.anyMessageContaining("user-1"),
				"logs should reference the identifier"
		);

		assertTrue(
				logger.anyMessageContaining("Request allowed"),
				"expected an allowed-decision debug log"
		);

		assertTrue(
				logger.anyMessageContaining("Request denied"),
				"expected a denied-decision debug log"
		);

		assertTrue(
				logger.any(Logger.Level.DEBUG),
				"expected debug-level logs from the pipeline"
		);
	}

	@Test
	void buildDefaultsToSilentLogger() {

		// Arrange

		RateLimit<FixedWindowPolicy> rateLimit =
				RateLimit.build(
						Algorithm.fixedWindow(),
						Persistence.inMemory()
				);

		FixedWindowPolicy policy =
				new FixedWindowPolicy(
						2,
						java.time.Duration.ofMinutes(1)
				);

		// Act - should produce no output and no error

		RateLimitResult result =
				rateLimit.use("user-1", policy);

		// Assert

		assertTrue(result.isAllowed());
	}
}