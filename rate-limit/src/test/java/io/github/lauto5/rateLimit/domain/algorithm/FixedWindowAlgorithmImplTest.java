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
			assertEquals(retryAfterExpectative , decision.getRetryAfter());

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

	}
	
}
