package io.github.lauto5.rateLimit.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;

class RateLimitResultMapperTest {

	private static final Instant FIXED_NOW =
			Instant.parse("2026-01-01T10:00:00Z");

	private static final Instant RESET_AT =
			FIXED_NOW.plus(Duration.ofMinutes(1));

	private static final Duration EXPIRES_IN =
			Duration.ofMinutes(1);

	private static final Duration RETRY_AFTER =
			Duration.ofSeconds(30);

	// ============================================================
	// HELPERS
	// ============================================================

	private FixedWindowState stateWith(int count) {
		return new FixedWindowState(
				count,
				FIXED_NOW
		);
	}

	private AlgorithmResult<FixedWindowState> allowedResult(
			FixedWindowState state,
			int remaining) {

		return AlgorithmResult.allowed(
				state,
				remaining,
				RESET_AT,
				EXPIRES_IN
		);
	}

	private AlgorithmResult<FixedWindowState> deniedResult(
			FixedWindowState state) {

		return AlgorithmResult.denied(
				state,
				RETRY_AFTER,
				RESET_AT,
				EXPIRES_IN
		);
	}

	private AtomicOperationResult<FixedWindowState> atomicResult(
			AlgorithmResult<FixedWindowState> algorithmResult) {

		return new AtomicOperationResult<>(
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
		void shouldMapAllowedDecision() {

			// Arrange

			FixedWindowState state =
					stateWith(1);

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
					result.getRetryAfter().isEmpty()
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
		void shouldMapDeniedDecision() {

			// Arrange

			FixedWindowState state =
					stateWith(10);

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
					RETRY_AFTER,
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
		void shouldRejectNullAtomicOperationResult() {

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
