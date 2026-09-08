package io.github.lauto5.rateLimit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.RateLimitService;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.testdoubles.FakeRateLimitStore;
import io.github.lauto5.rateLimit.testdoubles.FixedWindowTestFixtures;
import io.github.lauto5.rateLimit.testdoubles.StubRateLimitAlgorithm;
import io.github.lauto5.rateLimit.testdoubles.TestTime;

class RateLimitServiceUnitTest {

	private static final Instant FIXED_NOW =
			TestTime.FIXED_NOW;

	private static final Duration ONE_MINUTE =
			FixedWindowTestFixtures.ONE_MINUTE;

	private static final int DEFAULT_LIMIT = 10;

	private Clock fixedClock;

	private FixedWindowPolicy standardPolicy;

	private StubRateLimitAlgorithm<
			FixedWindowState,
			FixedWindowPolicy> algorithm;

	private FakeRateLimitStore store;

	private RateLimitService<
			FixedWindowState,
			FixedWindowPolicy> service;


	// ============================================================
	// SETUP
	// ============================================================

	@BeforeEach
	void setUp() {

		fixedClock = Clock.fixed(
				FIXED_NOW,
				ZoneOffset.UTC
		);

		standardPolicy =
				new FixedWindowPolicy(
						DEFAULT_LIMIT,
						ONE_MINUTE
				);

		FixedWindowState state =
				FixedWindowTestFixtures.stateWith(1);

		AlgorithmResult<FixedWindowState> algorithmResult =
				FixedWindowTestFixtures.allowedResult(
						state,
						DEFAULT_LIMIT - 1,
						FIXED_NOW.plus(ONE_MINUTE),
						ONE_MINUTE
				);

		AtomicOperationResult<FixedWindowState> operationResult =
				FixedWindowTestFixtures.operationResult(
						FIXED_NOW.plus(ONE_MINUTE),
						algorithmResult
				);

		algorithm =
				new StubRateLimitAlgorithm<>(
						state,
						algorithmResult
				);

		store =
				new FakeRateLimitStore(
						operationResult
				);

		service =
				new RateLimitService<>(
						store,
						algorithm,
						fixedClock
				);
	}


	// ============================================================
	// HELPERS
	// ============================================================

	private void wireServiceWith(
			AtomicOperationResult<FixedWindowState> operationResult) {

		store =
				new FakeRateLimitStore(operationResult);

		service =
				new RateLimitService<>(
						store,
						algorithm,
						fixedClock
				);
	}


	// ============================================================
	// TESTS
	// ============================================================

	@Nested
	class BasicCases {

		@Test
		void storeOperationShouldBeExecuted() {

			// Arrange

			String identifier = "user-1";

			// Act

			service.execute(
					identifier,
					standardPolicy
			);

			// Assert

			assertEquals(
					identifier,
					store.getReceivedIdentifier()
			);

			assertNotNull(
					store.getReceivedOperation()
			);
		}


		@Test
		void injectedClockShouldBeUsed() {

			// Arrange

			String identifier = "user-1";

			// Act

			service.execute(
					identifier,
					standardPolicy
			);

			// Assert

			AtomicOperation<?> operation =
					store.getReceivedOperation();

			assertNotNull(operation);

			assertEquals(
					FIXED_NOW,
					operation.getNow()
			);
		}


		@Test
		void allowedResultShouldBeReturned() {

			// Arrange
			// The setUp() already wires the service with this allowed result.

			int remaining = 9;

			Instant resetAt =
					FIXED_NOW.plus(ONE_MINUTE);

			// Act

			RateLimitResult result =
					service.execute(
							"user-1",
							standardPolicy
					);

			// Assert

			assertTrue(result.isAllowed());

			assertEquals(
					remaining,
					result.getRemaining()
			);

			assertTrue(
					!result.getRetryAfter().isPresent()
			);

			assertEquals(
					resetAt,
					result.getResetAt()
			);
		}


		@Test
		void deniedResultShouldBeReturned() {

			// Arrange

			FixedWindowState state =
					FixedWindowTestFixtures.stateWith(10, FIXED_NOW);

			Duration retryAfter =
					Duration.ofSeconds(30);

			Instant resetAt =
					FIXED_NOW.plus(ONE_MINUTE);

			AlgorithmResult<FixedWindowState> algorithmResult =
					FixedWindowTestFixtures.deniedResult(
							state,
							retryAfter,
							resetAt,
							ONE_MINUTE
					);

			AtomicOperationResult<FixedWindowState> operationResult =
					FixedWindowTestFixtures.operationResult(
							resetAt,
							algorithmResult
					);

			wireServiceWith(operationResult);

			// Act

			RateLimitResult result =
					service.execute(
							"user-1",
							standardPolicy
					);

			// Assert

			assertFalse(result.isAllowed());

			assertEquals(
					0,
					result.getRemaining()
			);

			assertTrue(
					result.getRetryAfter().isPresent()
			);

			assertEquals(
					retryAfter,
					result.getRetryAfter().get()
			);

			assertEquals(
					resetAt,
					result.getResetAt()
			);
		}

	}
}
