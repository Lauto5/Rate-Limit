package io.github.lauto5.rateLimit.domain.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowLogState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowLogPolicy;

public class SlidingWindowLogAlgorithmImplTest {

	private SlidingWindowLogAlgorithm algorithm;
	private SlidingWindowLogPolicy standardPolicy;
	private Instant fixedNow;

	@BeforeEach
	void setUp() {
		algorithm = new SlidingWindowLogAlgorithmImpl();
		standardPolicy = new SlidingWindowLogPolicy(5, Duration.ofMinutes(1));
		fixedNow = Instant.parse("2026-01-01T10:00:00Z");
	}

	// ==================== HELPER ====================

	private AlgorithmContext contextAt(Instant instant) {
		return new AlgorithmContext(instant);
	}

	private SlidingWindowLogState stateWith(List<Long> timestamps) {
		return new SlidingWindowLogState(timestamps);
	}

	private SlidingWindowLogState stateWithTimestampsAt(Instant... instants) {

		List<Long> timestamps = new ArrayList<>();

		for (Instant instant : instants) {
			timestamps.add(instant.toEpochMilli());
		}

		return stateWith(timestamps);
	}

	private SlidingWindowLogState stateWithNTimestampsAt(int count, Instant instant) {

		List<Long> timestamps = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			timestamps.add(instant.toEpochMilli());
		}

		return stateWith(timestamps);
	}

	private SlidingWindowLogPolicy policyWith(int limit, Duration windowSize) {
		return new SlidingWindowLogPolicy(limit, windowSize);
	}

	private AlgorithmResult<SlidingWindowLogState> executeAlgorithm(SlidingWindowLogState state,
			AlgorithmContext context) {
		return algorithm.execute(state, standardPolicy, context);
	}

	private AlgorithmResult<SlidingWindowLogState> executeAlgorithmWithPolicy(SlidingWindowLogState state,
			SlidingWindowLogPolicy policy, AlgorithmContext context) {
		return algorithm.execute(state, policy, context);
	}

	private AllowedDecision extractAllowed(AlgorithmResult<SlidingWindowLogState> result) {
		assertTrue(result.isAllowed(), "Expected decision to be ALLOWED");
		return assertInstanceOf(AllowedDecision.class, result.getDecision());
	}

	private DeniedDecision extractDenied(AlgorithmResult<SlidingWindowLogState> result) {
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
			SlidingWindowLogState initialState = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			List<Long> timestamps = result.getState().getTimestamps();

			assertEquals(4, decision.getRemaining());
			assertEquals(1, timestamps.size());
			assertEquals(Long.valueOf(fixedNow.toEpochMilli()), timestamps.get(0));

		}

		@Test
		void requestAfterLimitShouldBeDenied() {

			// Arrange
			SlidingWindowLogState initialState = stateWithNTimestampsAt(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);

		}

	}

	@Nested
	class RemainingCases {

		@Test
		void remainingShouldAccountForExistingTimestamps() {

			// Arrange
			SlidingWindowLogState initialState = stateWithNTimestampsAt(2, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(2, decision.getRemaining());

		}

		@Test
		void remainingShouldBeZeroAtLimitBoundary() {

			// Arrange
			SlidingWindowLogState initialState = stateWithNTimestampsAt(4, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

	}

	@Nested
	class PruningCases {

		@Test
		void timestampsOutsideWindowShouldBeExcludedFromCount() {

			// Arrange
			SlidingWindowLogState initialState = stateWithTimestampsAt(fixedNow.minusSeconds(120));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(4, decision.getRemaining());

		}

		@Test
		void timestampsOutsideWindowShouldBeRemovedFromResultingState() {

			// Arrange
			long oldTimestamp = fixedNow.minusSeconds(120).toEpochMilli();
			SlidingWindowLogState initialState = stateWithTimestampsAt(fixedNow.minusSeconds(120));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			List<Long> newTimestamps = result.getState().getTimestamps();

			assertFalse(newTimestamps.contains(oldTimestamp));
			assertEquals(1, newTimestamps.size());

		}

		@Test
		void timestampExactlyAtWindowStartShouldBeExcluded() {

			// Arrange
			// Un timestamp registrado hace EXACTAMENTE windowSize
			// ya no debe contar dentro de la ventana.
			SlidingWindowLogState initialState = stateWithNTimestampsAt(5, fixedNow.minusSeconds(60));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(4, decision.getRemaining());

		}

		@Test
		void timestampJustInsideWindowShouldBeIncluded() {

			// Arrange
			SlidingWindowLogState initialState = stateWithNTimestampsAt(5, fixedNow.minusSeconds(59));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);

		}

	}

	@Nested
	class TimestampCases {

		@Test
		void allowedRequestShouldAppendCurrentTimestamp() {

			// Arrange
			SlidingWindowLogState initialState = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			List<Long> timestamps = result.getState().getTimestamps();

			assertEquals(1, timestamps.size());
			assertEquals(Long.valueOf(fixedNow.toEpochMilli()), timestamps.get(0));

		}

		@Test
		void deniedRequestShouldNotAppendNewTimestamp() {

			// Arrange
			SlidingWindowLogState initialState = stateWithNTimestampsAt(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertEquals(5, result.getState().getTimestamps().size());

		}

	}

	@Nested
	class TimeCases {

		@Test
		void expireInShouldEqualWindowSizeWhenAllowed() {

			// Arrange
			SlidingWindowLogState initialState = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(standardPolicy.getWindowSize(), result.getExpireIn());

		}

		@Test
		void expireInShouldEqualWindowSizeWhenDenied() {

			// Arrange
			SlidingWindowLogState initialState = stateWithNTimestampsAt(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertEquals(standardPolicy.getWindowSize(), result.getExpireIn());

		}

		@Test
		void resetAtShouldBeNowPlusWindowSizeWhenAllowed() {

			// Arrange
			SlidingWindowLogState initialState = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);
			Instant expectedResetAt = fixedNow.plus(standardPolicy.getWindowSize());

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(expectedResetAt, result.getResetAt());

		}

		@Test
		void retryAfterShouldMatchTimeUntilOldestTimestampExpires() {

			// Arrange
			SlidingWindowLogPolicy singleSlotPolicy = policyWith(1, Duration.ofSeconds(60));
			SlidingWindowLogState initialState = stateWithTimestampsAt(fixedNow.minusSeconds(30));
			AlgorithmContext context = contextAt(fixedNow);
			Duration expectedRetryAfter = Duration.ofSeconds(30);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithmWithPolicy(initialState, singleSlotPolicy,
					context);

			// Assert
			DeniedDecision decision = extractDenied(result);
			assertEquals(expectedRetryAfter, decision.getRetryAfter());

		}

		@Test
		void retryAfterShouldMatchTheOldestAmongMultipleTimestamps() {

			// Arrange
			SlidingWindowLogState initialState = stateWithTimestampsAt(
					fixedNow.minusSeconds(10),
					fixedNow.minusSeconds(45), // el mas viejo -> determina el retryAfter
					fixedNow.minusSeconds(20),
					fixedNow.minusSeconds(5),
					fixedNow.minusSeconds(1)
			);
			AlgorithmContext context = contextAt(fixedNow);
			Duration expectedRetryAfter = Duration.ofSeconds(15); // 60 - 45

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);
			assertEquals(expectedRetryAfter, decision.getRetryAfter());

		}

		@Test
		void resetAtShouldBeNowPlusRetryAfterWhenDenied() {

			// Arrange
			SlidingWindowLogState initialState = stateWithNTimestampsAt(5, fixedNow.minusSeconds(30));
			AlgorithmContext context = contextAt(fixedNow);
			Instant expectedResetAt = fixedNow.plusSeconds(30);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertEquals(expectedResetAt, result.getResetAt());

		}

	}

	@Nested
	class ImmutabilityCases {

		@Test
		void allowedRequestShouldCreateNewState() {

			// Arrange
			SlidingWindowLogState initialState = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(initialState, context);

			// Assert
			assertNotSame(initialState, result.getState());

		}

		@Test
		void algorithmShouldNeverMutateOriginalTimestampsList() {

			// Arrange
			List<Long> originalTimestamps = new ArrayList<>(Arrays.asList(fixedNow.toEpochMilli()));
			SlidingWindowLogState initialState = stateWith(originalTimestamps);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			executeAlgorithm(initialState, context);

			// Assert
			assertEquals(1, originalTimestamps.size());
			assertEquals(Long.valueOf(fixedNow.toEpochMilli()), originalTimestamps.get(0));

		}

	}

	@Nested
	class PolicyCases {

		@Test
		void shouldRespectCustomLimit() {

			// Arrange
			SlidingWindowLogPolicy customPolicy = policyWith(1, Duration.ofMinutes(1));
			SlidingWindowLogState initialState = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithmWithPolicy(initialState, customPolicy,
					context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

		@Test
		void shouldRespectCustomWindowSize() {

			// Arrange
			SlidingWindowLogPolicy customPolicy = policyWith(5, Duration.ofSeconds(30));
			SlidingWindowLogState initialState = stateWithTimestampsAt(fixedNow.minusSeconds(40)); // fuera de 30s

			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<SlidingWindowLogState> result = executeAlgorithmWithPolicy(initialState, customPolicy,
					context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(4, decision.getRemaining());

		}

	}

	@Nested
	class SequentialExecutionCases {

		@Test
		void multipleAllowedRequestsShouldAccumulateTimestamps() {

			// Arrange
			SlidingWindowLogState state = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);

			// Act & Assert
			AlgorithmResult<SlidingWindowLogState> result1 = executeAlgorithm(state, context);
			assertEquals(4, extractAllowed(result1).getRemaining());
			state = result1.getState();

			AlgorithmResult<SlidingWindowLogState> result2 = executeAlgorithm(state, context);
			assertEquals(3, extractAllowed(result2).getRemaining());
			state = result2.getState();

			AlgorithmResult<SlidingWindowLogState> result3 = executeAlgorithm(state, context);
			assertEquals(2, extractAllowed(result3).getRemaining());
			state = result3.getState();

			assertEquals(3, state.getTimestamps().size());

		}

		@Test
		void requestsShouldBeDeniedOnceLimitReached() {

			// Arrange
			SlidingWindowLogState state = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);

			// Act & Assert - Consumir el limite de 5
			for (int i = 1; i <= 5; i++) {
				AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(state, context);
				assertTrue(result.isAllowed(), "Request " + i + " should be allowed");
				state = result.getState();
			}

			// Request 6 - Debe ser denegada
			AlgorithmResult<SlidingWindowLogState> deniedResult = executeAlgorithm(state, context);
			assertFalse(deniedResult.isAllowed(), "Request 6 should be denied");

		}

		@Test
		void shouldAllowAgainOnceOldestTimestampSlidesOutOfWindow() {

			// Arrange
			SlidingWindowLogState state = stateWith(new ArrayList<>());
			AlgorithmContext context = contextAt(fixedNow);

			for (int i = 1; i <= 5; i++) {
				AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(state, context);
				state = result.getState();
			}

			// Act - Todavia dentro de la ventana, debe denegar
			AlgorithmResult<SlidingWindowLogState> stillDenied = executeAlgorithm(state, context);
			assertFalse(stillDenied.isAllowed(), "Should still be denied within the same window");

			// Act - Pasan 61s, el timestamp mas antiguo ya salio de la ventana
			AlgorithmContext laterContext = contextAt(fixedNow.plusSeconds(61));
			AlgorithmResult<SlidingWindowLogState> laterResult = executeAlgorithm(state, laterContext);

			// Assert
			assertTrue(laterResult.isAllowed(), "Should be allowed once the oldest timestamp slides out");

		}

		@Test
		void retryAfterShouldBeExactNotApproximate() {

			// Arrange

			SlidingWindowLogState state = stateWith(new ArrayList<>());
			AlgorithmContext fillContext = contextAt(fixedNow);

			for (int i = 1; i <= 5; i++) {
				AlgorithmResult<SlidingWindowLogState> result = executeAlgorithm(state, fillContext);
				state = result.getState();
			}

			AlgorithmResult<SlidingWindowLogState> deniedResult = executeAlgorithm(state, fillContext);
			DeniedDecision decision = extractDenied(deniedResult);
			Duration retryAfter = decision.getRetryAfter();

			// Act - Un milisegundo antes de retryAfter, sigue denegado
			AlgorithmContext justBeforeContext = contextAt(fixedNow.plus(retryAfter).minusMillis(1));
			AlgorithmResult<SlidingWindowLogState> justBeforeResult = executeAlgorithm(state, justBeforeContext);

			// Act - Exactamente en retryAfter, ya permite
			AlgorithmContext exactContext = contextAt(fixedNow.plus(retryAfter));
			AlgorithmResult<SlidingWindowLogState> exactResult = executeAlgorithm(state, exactContext);

			// Assert
			assertFalse(justBeforeResult.isAllowed(), "Should still be denied 1ms before retryAfter");
			assertTrue(exactResult.isAllowed(), "Should be allowed exactly at retryAfter");

		}

	}

}
