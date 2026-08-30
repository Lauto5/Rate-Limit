package io.github.lauto5.rateLimit.domain.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.domain.algorithmState.LeakyBucketState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;
import io.github.lauto5.rateLimit.domain.policies.LeakyBucketPolicy;

public class LeakyBucketAlgorithmImplTest {

	private LeakyBucketAlgorithm algorithm;
	private LeakyBucketPolicy standardPolicy;
	private Instant fixedNow;

	@BeforeEach
	void setUp() {
		algorithm = new LeakyBucketAlgorithmImpl();
		standardPolicy = new LeakyBucketPolicy(5.0, 1.0); // capacidad 5, drena 1/seg
		fixedNow = Instant.parse("2026-01-01T10:00:00Z");
	}

	// ==================== HELPER ====================

	private AlgorithmContext contextAt(Instant instant) {
		return new AlgorithmContext(instant);
	}

	private LeakyBucketState stateWith(double water, Instant lastLeak) {
		return new LeakyBucketState(water, lastLeak.toEpochMilli());
	}

	private LeakyBucketPolicy policyWith(double capacity, double leakRate) {
		return new LeakyBucketPolicy(capacity, leakRate);
	}

	private AlgorithmResult<LeakyBucketState> executeAlgorithm(LeakyBucketState state, AlgorithmContext context) {
		return algorithm.execute(state, standardPolicy, context);
	}

	private AlgorithmResult<LeakyBucketState> executeAlgorithmWithPolicy(LeakyBucketState state,
			LeakyBucketPolicy policy, AlgorithmContext context) {
		return algorithm.execute(state, policy, context);
	}

	private AllowedDecision extractAllowed(AlgorithmResult<LeakyBucketState> result) {
		assertTrue(result.isAllowed(), "Expected decision to be ALLOWED");
		return assertInstanceOf(AllowedDecision.class, result.getDecision());
	}

	private DeniedDecision extractDenied(AlgorithmResult<LeakyBucketState> result) {
		assertFalse(result.isAllowed(), "Expected decision to be DENIED");
		return assertInstanceOf(DeniedDecision.class, result.getDecision());
	}

	// ==================== HELPER ====================

	// ==================== TESTS ====================

	@Nested
	class BasicCases {

		@Test
		void firstRequestShouldBeAllowed() {

			// Arrange
			LeakyBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			LeakyBucketState newState = result.getState();

			assertEquals(1.0, newState.getWater());
			assertEquals(4, decision.getRemaining());
			assertEquals(fixedNow.toEpochMilli(), newState.getLastLeak());
			assertNotSame(initialState, newState);

		}

		@Test
		void requestWhenBucketFullShouldBeDenied() {

			// Arrange
			LeakyBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);

			assertEquals(Duration.ofSeconds(1), decision.getRetryAfter());
			assertEquals(5.0, result.getState().getWater());

		}

		@Test
		void waterShouldLeakOverTime() {

			// Arrange
			LeakyBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(3));

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			LeakyBucketState newState = result.getState();

			assertEquals(3.0, newState.getWater());
			assertEquals(2, decision.getRemaining());
			assertEquals(context.getNow().toEpochMilli(), newState.getLastLeak());

		}

	}

	@Nested
	class BoundaryCases {

		@Test
		void shouldAllowExactlyAtCapacityBoundary() {

			// Arrange
			LeakyBucketState initialState = stateWith(4.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);

			assertEquals(5.0, result.getState().getWater());
			assertEquals(0, decision.getRemaining());

		}

		@Test
		void waterShouldNotGoBelowZeroWhenLeakingExcessively() {

			// Arrange
			LeakyBucketState initialState = stateWith(1.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(10)); // drenaria 10, mas de lo que hay

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);

			assertEquals(1.0, result.getState().getWater()); // 0 (piso) + 1 de esta request
			assertEquals(4, decision.getRemaining());

		}

	}

	@Nested
	class RemainingCases {

		@Test
		void remainingShouldDecreaseAfterEachAllowedRequest() {

			// Arrange
			LeakyBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result1 = executeAlgorithm(initialState, context);
			AlgorithmResult<LeakyBucketState> result2 = executeAlgorithm(result1.getState(), context);

			// Assert
			assertEquals(4, extractAllowed(result1).getRemaining());
			assertEquals(3, extractAllowed(result2).getRemaining());

		}

		@Test
		void remainingShouldTruncateFractionalWater() {

			// Arrange
			LeakyBucketState initialState = stateWith(2.3, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);

			assertEquals(1, decision.getRemaining()); // (int) (5 - 3.3) = 1
			assertEquals(3.3, result.getState().getWater(), 0.0001);

		}

	}

	@Nested
	class WaterCases {

		@Test
		void allowedRequestShouldIncrementWaterByOne() {

			// Arrange
			LeakyBucketState initialState = stateWith(2.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(3.0, result.getState().getWater());

		}

		@Test
		void deniedRequestShouldNotAddRequestCost() {

			// Arrange
			LeakyBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertEquals(5.0, result.getState().getWater());

		}

		@Test
		void leakShouldReduceWaterBeforeEvaluatingRequest() {

			// Arrange
			LeakyBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(2));

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(4.0, result.getState().getWater());

		}

	}

	@Nested
	class TimeCases {

		@Test
		void retryAfterShouldMatchExcessWaterDividedByLeakRate() {

			// Arrange
			LeakyBucketState initialState = stateWith(7.0, fixedNow); // por encima de la capacidad
			AlgorithmContext context = contextAt(fixedNow);
			Duration expectedRetryAfter = Duration.ofSeconds(3); // excedente=3, leakRate=1/seg

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);
			assertEquals(expectedRetryAfter, decision.getRetryAfter());

		}

		@Test
		void expireInShouldMatchTimeUntilBucketEmpties() {

			// Arrange
			LeakyBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			Duration expectedExpireIn = Duration.ofSeconds(1); // newWater=1, leakRate=1/seg

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(expectedExpireIn, result.getExpireIn());

		}

		@Test
		void resetAtShouldBeNowPlusExpireInWhenAllowed() {

			// Arrange
			LeakyBucketState initialState = stateWith(2.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			Instant expectedResetAt = fixedNow.plusSeconds(3); // newWater=3

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(expectedResetAt, result.getResetAt());

		}

		@Test
		void resetAtShouldBeNowPlusRetryAfterWhenDenied() {

			// Arrange
			LeakyBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			Instant expectedResetAt = fixedNow.plusSeconds(1);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertEquals(expectedResetAt, result.getResetAt());

		}

	}

	@Nested
	class ImmutabilityCases {

		@Test
		void allowedRequestShouldCreateNewStateInstance() {

			// Arrange
			LeakyBucketState initialState = stateWith(3.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			assertNotSame(initialState, result.getState());

		}

		@Test
		void algorithmShouldNeverMutateOriginalState() {

			// Arrange
			LeakyBucketState initialState = stateWith(3.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(5));
			double originalWater = initialState.getWater();
			long originalLastLeak = initialState.getLastLeak();

			// Act
			executeAlgorithm(initialState, context);

			// Assert
			assertEquals(originalWater, initialState.getWater());
			assertEquals(originalLastLeak, initialState.getLastLeak());

		}

	}

	@Nested
	class PolicyCases {

		@Test
		void shouldRespectCustomCapacity() {

			// Arrange
			LeakyBucketPolicy customPolicy = policyWith(2.0, 1.0);
			LeakyBucketState initialState = stateWith(1.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

		@Test
		void shouldRespectCustomLeakRate() {

			// Arrange
			LeakyBucketPolicy customPolicy = policyWith(5.0, 2.0); // 2/seg
			LeakyBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(1));

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(1, decision.getRemaining());

		}

		@Test
		void shouldRespectSlowLeakRate() {

			// Arrange
			LeakyBucketPolicy customPolicy = policyWith(5.0, 0.5); // 0.5/seg
			LeakyBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(2));

			// Act
			AlgorithmResult<LeakyBucketState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

	}

	@Nested
	class SequentialExecutionCases {

		@Test
		void multipleAllowedRequestsShouldIncreaseWaterSequentially() {

			// Arrange
			LeakyBucketState state = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act & Assert
			AlgorithmResult<LeakyBucketState> result1 = executeAlgorithm(state, context);
			assertEquals(4, extractAllowed(result1).getRemaining());
			state = result1.getState();

			AlgorithmResult<LeakyBucketState> result2 = executeAlgorithm(state, context);
			assertEquals(3, extractAllowed(result2).getRemaining());
			state = result2.getState();

			AlgorithmResult<LeakyBucketState> result3 = executeAlgorithm(state, context);
			assertEquals(2, extractAllowed(result3).getRemaining());

		}

		@Test
		void requestsShouldBeDeniedOnceBucketIsFull() {

			// Arrange
			LeakyBucketState state = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act & Assert - Llenar el balde hasta la capacidad (5)
			for (int i = 1; i <= 5; i++) {
				AlgorithmResult<LeakyBucketState> result = executeAlgorithm(state, context);
				assertTrue(result.isAllowed(), "Request " + i + " should be allowed");
				state = result.getState();
			}

			// Request 6 - Debe ser denegada
			AlgorithmResult<LeakyBucketState> deniedResult = executeAlgorithm(state, context);
			assertFalse(deniedResult.isAllowed(), "Request 6 should be denied");

		}

		@Test
		void bucketShouldDrainAfterBeingFull() {

			// Arrange
			LeakyBucketState state = stateWith(5.0, fixedNow);
			AlgorithmContext fullContext = contextAt(fixedNow);

			// Act - Solicitud sin espacio disponible
			AlgorithmResult<LeakyBucketState> deniedResult = executeAlgorithm(state, fullContext);
			assertFalse(deniedResult.isAllowed(), "Should be denied when bucket is full");
			state = deniedResult.getState();

			// Act - Pasan 5 segundos, drena 5 unidades
			AlgorithmContext drainedContext = contextAt(fixedNow.plusSeconds(5));
			AlgorithmResult<LeakyBucketState> allowedResult = executeAlgorithm(state, drainedContext);

			// Assert
			AllowedDecision decision = extractAllowed(allowedResult);
			assertEquals(4, decision.getRemaining());

		}

	}

}
