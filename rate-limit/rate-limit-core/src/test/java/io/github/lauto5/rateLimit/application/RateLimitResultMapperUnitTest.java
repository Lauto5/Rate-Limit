package io.github.lauto5.rateLimit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.RateLimitResultMapper;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.testdoubles.FixedWindowTestFixtures;

class RateLimitResultMapperUnitTest {

	private static final Instant RESET_AT =
			FixedWindowTestFixtures.FIXED_NOW.plus(FixedWindowTestFixtures.ONE_MINUTE);

	// ============================================================
	// HELPERS
	// ============================================================

	private AlgorithmResult<FixedWindowState> allowedResult(
			FixedWindowState state,
			int remaining) {

		return FixedWindowTestFixtures.allowedResult(
				state,
				remaining,
				RESET_AT,
				FixedWindowTestFixtures.ONE_MINUTE
		);
	}

	private AlgorithmResult<FixedWindowState> deniedResult(
			FixedWindowState state) {

		return FixedWindowTestFixtures.deniedResult(
				state,
				FixedWindowTestFixtures.RETRY_AFTER,
				RESET_AT,
				FixedWindowTestFixtures.ONE_MINUTE
		);
	}

	private AtomicOperationResult<FixedWindowState> atomicResult(
			AlgorithmResult<FixedWindowState> algorithmResult) {

		return FixedWindowTestFixtures.operationResult(
				RESET_AT,
				algorithmResult
		);
	}

	// ============================================================
	// TESTS
	// ============================================================

	@Nested
	class AllowedCases {

		@Test
		void allowedDecisionShouldBeMapped() {

			// Arrange

			FixedWindowState state =
					FixedWindowTestFixtures.stateWith(1);

			int remaining = 9;

			AlgorithmResult<FixedWindowState> algorithmResult =
					allowedResult(
							state,
							remaining
					);

			AtomicOperationResult<FixedWindowState> atomicResult =
					atomicResult(algorithmResult);

			// Act

			RateLimitResult result =
					RateLimitResultMapper
							.fromAtomicOperationResult(
									atomicResult
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
					RESET_AT,
					result.getResetAt()
			);
		}
	}

	@Nested
	class DeniedCases {

		@Test
		void deniedDecisionShouldBeMapped() {

			// Arrange

			FixedWindowState state =
					FixedWindowTestFixtures.stateWith(10);

			AlgorithmResult<FixedWindowState> algorithmResult =
					deniedResult(state);

			AtomicOperationResult<FixedWindowState> atomicResult =
					atomicResult(algorithmResult);

			// Act

			RateLimitResult result =
					RateLimitResultMapper
							.fromAtomicOperationResult(
									atomicResult
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
					FixedWindowTestFixtures.RETRY_AFTER,
					result.getRetryAfter().get()
			);

			assertEquals(
					RESET_AT,
					result.getResetAt()
			);
		}
	}

	@Nested
	class ValidationCases {

		@Test
		void nullAtomicOperationResultShouldBeRejected() {

			// Arrange

			AtomicOperationResult<FixedWindowState> atomicResult =
					null;

			// Act & Assert

			IllegalArgumentException exception =
					assertThrows(
							IllegalArgumentException.class,
							() -> RateLimitResultMapper
									.fromAtomicOperationResult(
											atomicResult
									)
					);

			assertEquals(
					"AtomicOperationResult cannot be null",
					exception.getMessage()
			);
		}
	}
}
