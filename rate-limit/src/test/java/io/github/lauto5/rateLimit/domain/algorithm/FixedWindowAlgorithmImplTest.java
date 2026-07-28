package io.github.lauto5.rateLimit.domain.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

public class FixedWindowAlgorithmImplTest {

	private FixedWindowAlgorithm algorithm;
	private FixedWindowPolicy standardPolicy;
	private Instant fixedNow;

	@BeforeEach
	void setUp() {
		algorithm = new FixedWindowAlgorithmImpl();
		standardPolicy = new FixedWindowPolicy(5, Duration.ofMinutes(1));
		fixedNow = Instant.parse("2026-01-01T10:00:00Z");
	}

	// ==================== HELPER ====================

	private AlgorithmContext contextAt(Instant instant) {
		return new AlgorithmContext(instant);
	}

	private FixedWindowState stateWith(int count, Instant windowStart) {
		return new FixedWindowState(count, windowStart);
	}

	private FixedWindowPolicy policyWith(int limit, Duration duration) {
		return new FixedWindowPolicy(limit, duration);
	}

	private AlgorithmResult<FixedWindowState> executeAlgorithm(FixedWindowState state, AlgorithmContext context) {
		return algorithm.execute(state, standardPolicy, context);
	}
	
	private AlgorithmResult<FixedWindowState> executeAlgorithmWithPolicy(FixedWindowState state, 
			FixedWindowPolicy policy, AlgorithmContext context) {
		return algorithm.execute(state, policy, context);
	}

	private AllowedDecision extractAllowed(AlgorithmResult<FixedWindowState> result) {
		assertTrue(result.isAllowed(), "Expected decision to be ALLOWED");
		return assertInstanceOf(AllowedDecision.class, result.getDecision());
	}

	private DeniedDecision extractDenied(AlgorithmResult<FixedWindowState> result) {
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
			FixedWindowState initialState = stateWith(0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			FixedWindowState newState = result.getState();

			assertEquals(1, newState.getCount());
			assertEquals(4, decision.getRemaining());
			assertEquals(fixedNow, newState.getWindowStart());
			assertNotSame(initialState, newState, "Should create new state instance");

		}

		@Test
		void requestAfterLimitShouldBeDenied() {

			// Arrange
			FixedWindowState initialState = stateWith(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			Duration retryAfterExpectative = Duration.between(fixedNow, fixedNow.plus(standardPolicy.getWindowSize()));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);
			FixedWindowState returnedState = result.getState();

			assertSame(initialState, returnedState);
			assertEquals(retryAfterExpectative, decision.getRetryAfter());

		}

		@Test
		void expiredWindowShouldCreateNewState() {

			// Arrange
			FixedWindowState initialState = stateWith(0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plus(standardPolicy.getWindowSize()));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			FixedWindowState newState = result.getState();

			assertEquals(1, newState.getCount());
			assertEquals(standardPolicy.getLimit() - 1, decision.getRemaining());
			assertEquals(context.getNow(), newState.getWindowStart());
			assertNotSame(initialState, newState, "Should create new state instance");

		}

	}

	@Nested
	class BoundaryCases {

		@Test
		void windowShouldNotExpireBeforeBoundary() {

			// Arrange

			int remainingConsume = 3;
			FixedWindowState initialState = stateWith(remainingConsume, fixedNow);
			Duration borderWindowEnd = standardPolicy.getWindowSize().minus(Duration.ofSeconds(1));
			AlgorithmContext context = contextAt(fixedNow.plus(borderWindowEnd));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			FixedWindowState newState = result.getState();

			assertEquals(remainingConsume + 1, newState.getCount());
			assertEquals(standardPolicy.getLimit() - remainingConsume - 1, decision.getRemaining());
			assertEquals(fixedNow, newState.getWindowStart());
			assertNotSame(initialState, newState, "Should create new state instance");

		}

		@Test
		void windowShouldExpireExactlyAtBoundary() {

			// Arrange
			FixedWindowState initialState = stateWith(3, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plus(standardPolicy.getWindowSize()));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			FixedWindowState newState = result.getState();

			assertEquals(1, newState.getCount());
			assertEquals(4, decision.getRemaining());
			assertEquals(context.getNow(), newState.getWindowStart());
			assertNotSame(initialState, newState, "Should create new state instance");

		}

		@Test
		void windowShouldExpireAfterBoundary() {

			// Arrange
			FixedWindowState initialState = stateWith(3, fixedNow);
			AlgorithmContext context = contextAt(
					fixedNow.plus(standardPolicy.getWindowSize()).plus(Duration.ofSeconds(1)));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			FixedWindowState newState = result.getState();

			assertEquals(1, newState.getCount());
			assertEquals(4, decision.getRemaining());
			assertEquals(context.getNow(), newState.getWindowStart());
			assertNotSame(initialState, newState, "Should create new state instance");

		}

	}

	@Nested
	class RemainingCases {

		@Test
		void remainingShouldDecreaseAfterEachAllowedRequest() {

			// Arrange
			FixedWindowState initialState = stateWith(2, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);
			AlgorithmResult<FixedWindowState> result2 = executeAlgorithm(result.getState(), context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			AllowedDecision decision2 = extractAllowed(result2);
			
			assertEquals(2, decision.getRemaining());
			assertEquals(1, decision2.getRemaining());

		}

		@Test
		void lastAllowedRequestShouldLeaveZeroRemaining() {

			// Arrange
			FixedWindowState initialState = stateWith(4, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

		@Test
		void remainingShouldResetAfterWindowExpires() {

			// Arrange
			FixedWindowState initialState = stateWith(2, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plus(standardPolicy.getWindowSize()));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(4, decision.getRemaining());

		}

	}

	@Nested
	class CounterCases {

		@Test
		void allowedRequestShouldIncrementCounter() {

			// Arrange
			FixedWindowState initialState = stateWith(2, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			
			FixedWindowState newState = result.getState();
			assertEquals(3, newState.getCount());

		}

		@Test
		void deniedRequestShouldNotIncrementCounter() {

			// Arrange
			FixedWindowState initialState = stateWith(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			
			FixedWindowState returnedState = result.getState();
			assertEquals(5, returnedState.getCount());

		}

		@Test
		void expiredWindowShouldResetCounterToOne() {

			// Arrange
			FixedWindowState initialState = stateWith(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plus(standardPolicy.getWindowSize()));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			
			FixedWindowState newState = result.getState();
			assertEquals(1, newState.getCount());

		}

	}

	@Nested
	class TimeCases {

		@Test
		void retryAfterShouldMatchRemainingWindowTime() {

			// Arrange
			FixedWindowState initialState = stateWith(5, fixedNow);
			Duration elapsed = Duration.ofSeconds(45);
			AlgorithmContext context = contextAt(fixedNow.plus(elapsed));
			Duration expectedRetryAfter = standardPolicy.getWindowSize().minus(elapsed);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);
			assertEquals(expectedRetryAfter, decision.getRetryAfter());

		}

		@Test
		void expireInShouldMatchRemainingWindowTime() {

			// Arrange
			FixedWindowState initialState = stateWith(2, fixedNow);
			Duration elapsed = Duration.ofSeconds(45);
			AlgorithmContext context = contextAt(fixedNow.plus(elapsed));
			Duration expectedExpireIn = standardPolicy.getWindowSize().minus(elapsed);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			
			extractAllowed(result);
			
			assertEquals(expectedExpireIn, result.getExpireIn());

		}

		@Test
		void resetAtShouldBeWindowEnd() {

			// Arrange
			FixedWindowState initialState = stateWith(2, fixedNow);
			Duration elapsed = Duration.ofSeconds(45);
			AlgorithmContext context = contextAt(fixedNow.plus(elapsed));
			Instant expectedResetAt = fixedNow.plus(standardPolicy.getWindowSize());

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(expectedResetAt, result.getResetAt());

		}

		@Test
		void newWindowShouldCalculateNewResetAt() {

			// Arrange
			FixedWindowState initialState = stateWith(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plus(standardPolicy.getWindowSize()));
			Instant expectedResetAt = context.getNow().plus(standardPolicy.getWindowSize());

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(expectedResetAt, result.getResetAt());

		}

	}

	@Nested
	class ImmutabilityCases {

		@Test
		void allowedRequestShouldCreateNewState() {

			// Arrange
			FixedWindowState initialState = stateWith(2, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			assertNotSame(initialState, result.getState());

		}

		@Test
		void deniedRequestShouldReuseCurrentState() {

			// Arrange
			FixedWindowState initialState = stateWith(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithm(initialState, context);

			// Assert
			assertSame(initialState, result.getState());

		}

		@Test
		void algorithmShouldNeverMutateOriginalState() {

			// Arrange
			FixedWindowState initialState = stateWith(2, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			int originalCount = initialState.getCount();
			Instant originalWindowStart = initialState.getWindowStart();

			// Act
			executeAlgorithm(initialState, context);

			// Assert
			assertEquals(originalCount, initialState.getCount());
			assertEquals(originalWindowStart, initialState.getWindowStart());

		}

	}

	@Nested
	class PolicyCases {

		@Test
		void shouldRespectCustomLimit() {

			// Arrange
			FixedWindowPolicy customPolicy = policyWith(1, Duration.ofMinutes(1));
			FixedWindowState initialState = stateWith(0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

		@Test
		void shouldRespectCustomWindow() {

			// Arrange
			Duration customDuration = Duration.ofSeconds(10);
			FixedWindowPolicy customPolicy = policyWith(5, customDuration);
			FixedWindowState initialState = stateWith(5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plus(customDuration));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(4, decision.getRemaining());

		}

		@Test
		void shouldRespectLargeWindow() {

			// Arrange
			Duration largeDuration = Duration.ofHours(24);
			FixedWindowPolicy customPolicy = policyWith(100, largeDuration);
			FixedWindowState initialState = stateWith(50, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plus(Duration.ofHours(12)));

			// Act
			AlgorithmResult<FixedWindowState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(49, decision.getRemaining());

		}

	}

	@Nested
	class SequentialExecutionCases {

		@Test
		void multipleAllowedRequestsShouldDecreaseRemaining() {

			// Arrange
			FixedWindowState state = stateWith(0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act & Assert
			// Request 1
			AlgorithmResult<FixedWindowState> result1 = executeAlgorithm(state, context);
			AllowedDecision decision1 = extractAllowed(result1);
			assertEquals(4, decision1.getRemaining());
			state = result1.getState();

			// Request 2
			AlgorithmResult<FixedWindowState> result2 = executeAlgorithm(state, context);
			AllowedDecision decision2 = extractAllowed(result2);
			assertEquals(3, decision2.getRemaining());
			state = result2.getState();

			// Request 3
			AlgorithmResult<FixedWindowState> result3 = executeAlgorithm(state, context);
			AllowedDecision decision3 = extractAllowed(result3);
			assertEquals(2, decision3.getRemaining());

		}

		@Test
		void requestAfterSeveralAllowedRequestsShouldBeDenied() {

			// Arrange
			FixedWindowState state = stateWith(0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act & Assert - Hacer 5 peticiones permitidas
			for (int i = 1; i <= 5; i++) {
				AlgorithmResult<FixedWindowState> result = executeAlgorithm(state, context);
				assertTrue(result.isAllowed(), "Request " + i + " should be allowed");
				state = result.getState();
			}

			// Request 6 - Debe ser denegada
			AlgorithmResult<FixedWindowState> deniedResult = executeAlgorithm(state, context);
			assertFalse(deniedResult.isAllowed(), "Request 6 should be denied");

		}

		@Test
		void shouldBehaveLikeFixedWindow() {

			// Arrange
			FixedWindowState state = stateWith(0, fixedNow);
			Duration windowSize = standardPolicy.getWindowSize();

			// Window 1
			for (int i = 1; i <= 5; i++) {
				AlgorithmContext context = contextAt(fixedNow.plus(Duration.ofSeconds(i * 10)));
				AlgorithmResult<FixedWindowState> result = executeAlgorithm(state, context);
				assertTrue(result.isAllowed(), "Window 1 - Request " + i + " should be allowed");
				state = result.getState();
			}

			// Request 6
			AlgorithmContext contextBeforeExpiry = contextAt(fixedNow.plus(Duration.ofSeconds(55)));
			AlgorithmResult<FixedWindowState> deniedResult = executeAlgorithm(state, contextBeforeExpiry);
			assertFalse(deniedResult.isAllowed(), "Window 1 - Request 6 should be denied");

			// Window 2
			AlgorithmContext contextAfterExpiry = contextAt(fixedNow.plus(windowSize).plus(Duration.ofSeconds(1)));

			// Allowed
			AlgorithmResult<FixedWindowState> newWindowResult = executeAlgorithm(state, contextAfterExpiry);
			assertTrue(newWindowResult.isAllowed(), "Window 2 - First request should be allowed");
			AllowedDecision decision = extractAllowed(newWindowResult);
			assertEquals(1, newWindowResult.getState().getCount());
			assertEquals(4, decision.getRemaining());

			// Allowed 2
			FixedWindowState newState = newWindowResult.getState();
			AlgorithmResult<FixedWindowState> secondRequest = executeAlgorithm(newState, contextAfterExpiry);
			assertTrue(secondRequest.isAllowed(), "Window 2 - Second request should be allowed");
			AllowedDecision decision2 = extractAllowed(secondRequest);
			assertEquals(2, secondRequest.getState().getCount());
			assertEquals(3, decision2.getRemaining());

		}

	}

}
