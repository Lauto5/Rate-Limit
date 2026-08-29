package io.github.lauto5.rateLimit.domain.policies;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class TokenBucketPolicyTest {

	// ==================== TESTS ====================

	@Nested
	class ConstructionCases {

		@Test
		void shouldCreatePolicyWithValidValues() {

			// Arrange
			double capacity = 10.0;
			double refillRate = 2.5;

			// Act
			TokenBucketPolicy policy = new TokenBucketPolicy(capacity, refillRate);

			// Assert
			assertEquals(capacity, policy.getCapacity());
			assertEquals(refillRate, policy.getRefillRate());

		}

		@Test
		void shouldAcceptSmallestPositiveCapacityAndRefillRate() {

			// Act
			TokenBucketPolicy policy = assertDoesNotThrow(
					() -> new TokenBucketPolicy(0.0001, 0.0001)
			);

			// Assert
			assertEquals(0.0001, policy.getCapacity());
			assertEquals(0.0001, policy.getRefillRate());

		}

	}

	@Nested
	class CapacityValidationCases {

		@Test
		void shouldThrowWhenCapacityIsZero() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(0.0, 1.0)
			);

			// Assert
			assertTrue(exception.getMessage().contains("Capacity must be greater than 0"));

		}

		@Test
		void shouldThrowWhenCapacityIsNegative() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(-5.0, 1.0)
			);

			// Assert
			assertTrue(exception.getMessage().contains("Capacity must be greater than 0"));

		}

		@Test
		void shouldThrowWhenCapacityIsNaN() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(Double.NaN, 1.0)
			);

			// Assert
			assertTrue(exception.getMessage().contains("Capacity cannot be NaN"));

		}

		@Test
		void shouldThrowWhenCapacityIsPositiveInfinity() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(Double.POSITIVE_INFINITY, 1.0)
			);

			// Assert
			assertTrue(exception.getMessage().contains("Capacity must be finite"));

		}

		@Test
		void shouldThrowWhenCapacityIsNegativeInfinity() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(Double.NEGATIVE_INFINITY, 1.0)
			);

			// Assert
			assertTrue(exception.getMessage().contains("Capacity must be finite"));

		}

	}

	@Nested
	class RefillRateValidationCases {

		@Test
		void shouldThrowWhenRefillRateIsZero() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(5.0, 0.0)
			);

			// Assert
			assertTrue(exception.getMessage().contains("RefillRate must be greater than 0"));

		}

		@Test
		void shouldThrowWhenRefillRateIsNegative() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(5.0, -1.0)
			);

			// Assert
			assertTrue(exception.getMessage().contains("RefillRate must be greater than 0"));

		}

		@Test
		void shouldThrowWhenRefillRateIsNaN() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(5.0, Double.NaN)
			);

			// Assert
			assertTrue(exception.getMessage().contains("RefillRate cannot be NaN"));

		}

		@Test
		void shouldThrowWhenRefillRateIsPositiveInfinity() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(5.0, Double.POSITIVE_INFINITY)
			);

			// Assert
			assertTrue(exception.getMessage().contains("RefillRate must be finite"));

		}

		@Test
		void shouldThrowWhenRefillRateIsNegativeInfinity() {

			// Act
			IllegalArgumentException exception = assertThrows(
					IllegalArgumentException.class,
					() -> new TokenBucketPolicy(5.0, Double.NEGATIVE_INFINITY)
			);

			// Assert
			assertTrue(exception.getMessage().contains("RefillRate must be finite"));

		}

	}

}