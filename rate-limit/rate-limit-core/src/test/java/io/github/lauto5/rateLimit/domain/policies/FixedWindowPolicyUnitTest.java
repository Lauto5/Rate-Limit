package io.github.lauto5.rateLimit.domain.policies;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class FixedWindowPolicyUnitTest {

	// ==================== TESTS ====================

	@Nested
	class ConstructionCases {

		@Test
		void shouldCreatePolicyWithValidValues() {

			// Arrange
			int limit = 10;
			Duration windowSize = Duration.ofMinutes(1);

			// Act
			FixedWindowPolicy policy = new FixedWindowPolicy(limit, windowSize);

			// Assert
			assertEquals(limit, policy.getLimit());
			assertEquals(windowSize, policy.getWindowSize());

		}

		@Test
		void shouldAcceptWindowSizeExactlyOneSecond() {

			// Arrange
			Duration windowSize = Duration.ofSeconds(1);

			// Act
			FixedWindowPolicy policy = assertDoesNotThrow(
					() -> new FixedWindowPolicy(1, windowSize)
			);

			// Assert
			assertEquals(windowSize, policy.getWindowSize());

		}

	}

	@Nested
	class LimitValidationCases {

		@Test
		void shouldThrowWhenLimitIsZero() {

			// Arrange
			Duration windowSize = Duration.ofMinutes(1);

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new FixedWindowPolicy(0, windowSize)
			);

			// Assert
			assertTrue(exception.getMessage().contains("Limit must be greater than 0"));

		}

		@Test
		void shouldThrowWhenLimitIsNegative() {

			// Arrange
			Duration windowSize = Duration.ofMinutes(1);

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new FixedWindowPolicy(-5, windowSize)
			);

			// Assert
			assertTrue(exception.getMessage().contains("Limit must be greater than 0"));

		}

	}

	@Nested
	class WindowSizeValidationCases {

		@Test
		void shouldThrowWhenWindowSizeIsNull() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new FixedWindowPolicy(5, null)
			);

			// Assert
			assertTrue(exception.getMessage().contains("WindowSize cannot be null"));

		}

		@Test
		void shouldThrowWhenWindowSizeIsZero() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new FixedWindowPolicy(5, Duration.ZERO)
			);

			// Assert
			assertTrue(exception.getMessage().contains("WindowSize must be positive"));

		}

		@Test
		void shouldThrowWhenWindowSizeIsNegative() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new FixedWindowPolicy(5, Duration.ofSeconds(-1))
			);

			// Assert
			assertTrue(exception.getMessage().contains("WindowSize must be positive"));

		}

		@Test
		void shouldThrowWhenWindowSizeIsBelowOneSecond() {

			// Arrange
			Duration windowSize = Duration.ofMillis(999);

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new FixedWindowPolicy(5, windowSize)
			);

			// Assert
			assertTrue(exception.getMessage().contains("WindowSize must be at least 1 second"));

		}

	}

}