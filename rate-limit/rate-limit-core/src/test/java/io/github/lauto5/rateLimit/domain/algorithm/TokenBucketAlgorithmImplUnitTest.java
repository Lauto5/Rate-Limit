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

import io.github.lauto5.rateLimit.domain.algorithmState.TokenBucketState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;
import io.github.lauto5.rateLimit.domain.policies.TokenBucketPolicy;
import io.github.lauto5.rateLimit.testdoubles.FixedWindowTestFixtures;

public class TokenBucketAlgorithmImplUnitTest {

	private TokenBucketAlgorithm algorithm;
	private TokenBucketPolicy standardPolicy;
	private Instant fixedNow;

	@BeforeEach
	void setUp() {
		algorithm = new TokenBucketAlgorithmImpl();
		standardPolicy = new TokenBucketPolicy(5.0, 1.0); // 5 tokens, 1 token/sec
		fixedNow = FixedWindowTestFixtures.FIXED_NOW;
	}

	// ==================== HELPER ====================

	private AlgorithmContext contextAt(Instant instant) {
		return new AlgorithmContext(instant);
	}

	private TokenBucketState stateWith(double tokens, Instant lastRefill) {
		return new TokenBucketState(tokens, lastRefill);
	}

	private TokenBucketPolicy policyWith(double capacity, double refillRate) {
		return new TokenBucketPolicy(capacity, refillRate);
	}

	private AlgorithmResult<TokenBucketState> executeAlgorithm(TokenBucketState state, AlgorithmContext context) {
		return algorithm.execute(state, standardPolicy, context);
	}

	private AlgorithmResult<TokenBucketState> executeAlgorithmWithPolicy(TokenBucketState state,
			TokenBucketPolicy policy, AlgorithmContext context) {
		return algorithm.execute(state, policy, context);
	}

	private AllowedDecision extractAllowed(AlgorithmResult<TokenBucketState> result) {
		assertTrue(result.isAllowed(), "Expected decision to be ALLOWED");
		return assertInstanceOf(AllowedDecision.class, result.getDecision());
	}

	private DeniedDecision extractDenied(AlgorithmResult<TokenBucketState> result) {
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
			TokenBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			TokenBucketState newState = result.getState();

			assertEquals(4.0, newState.getTokens());
			assertEquals(4, decision.getRemaining());
			assertEquals(fixedNow, newState.getLastRefill());
			assertNotSame(initialState, newState, "Should create new state instance");

		}

		@Test
		void requestWithNoTokensShouldBeDenied() {

			// Arrange
			TokenBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);

			assertEquals(Duration.ofSeconds(1), decision.getRetryAfter());
			assertEquals(0.0, result.getState().getTokens());

		}

		@Test
		void tokensShouldRefillOverTime() {

			// Arrange
			TokenBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(3));

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			TokenBucketState newState = result.getState();

			assertEquals(2.0, newState.getTokens());
			assertEquals(2, decision.getRemaining());
			assertEquals(context.getNow(), newState.getLastRefill());

		}

	}

	@Nested
	class BoundaryCases {

		@Test
		void exactOneTokenShouldBeAllowed() {

			// Arrange
			TokenBucketState initialState = stateWith(1.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);

			assertEquals(0.0, result.getState().getTokens());
			assertEquals(0, decision.getRemaining());

		}

		@Test
		void justBelowOneTokenShouldBeDenied() {

			// Arrange
			TokenBucketState initialState = stateWith(0.5, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);

			assertEquals(Duration.ofMillis(500), decision.getRetryAfter());

		}

		@Test
		void refillingShouldNotExceedCapacity() {

			// Arrange
			TokenBucketState initialState = stateWith(4.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(10));

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);

			assertEquals(4.0, result.getState().getTokens());
			assertEquals(4, decision.getRemaining());

		}

	}

	@Nested
	class RemainingCases {

		@Test
		void remainingShouldDecreaseAfterEachAllowedRequest() {

			// Arrange
			TokenBucketState initialState = stateWith(3.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);
			AlgorithmResult<TokenBucketState> result2 = executeAlgorithm(result.getState(), context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			AllowedDecision decision2 = extractAllowed(result2);

			assertEquals(2, decision.getRemaining());
			assertEquals(1, decision2.getRemaining());

		}

		@Test
		void lastAllowedRequestShouldLeaveZeroRemaining() {

			// Arrange
			TokenBucketState initialState = stateWith(1.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

		@Test
		void remainingShouldTruncateFractionalTokens() {

			// Arrange
			TokenBucketState initialState = stateWith(2.7, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);

			assertEquals(1, decision.getRemaining());
			assertEquals(1.7, result.getState().getTokens(), 0.0001);

		}

	}

	@Nested
	class TokenCases {

		@Test
		void allowedRequestShouldDecrementTokensByOne() {

			// Arrange
			TokenBucketState initialState = stateWith(3.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(2.0, result.getState().getTokens());

		}

		@Test
		void deniedRequestShouldNotChangeTokenCount() {

			// Arrange
			TokenBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertEquals(0.0, result.getState().getTokens());

		}

		@Test
		void refillShouldIncreaseTokensBeforeConsuming() {

			// Arrange
			TokenBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(2));

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(1.0, result.getState().getTokens());

		}

	}

	@Nested
	class TimeCases {

		@Test
		void retryAfterShouldMatchMissingTokensDividedByRefillRate() {

			// Arrange
			TokenBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			Duration expectedRetryAfter = Duration.ofSeconds(1);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);
			assertEquals(expectedRetryAfter, decision.getRetryAfter());

		}

		@Test
		void expireInShouldMatchTimeUntilBucketIsFullAgain() {

			// Arrange
			TokenBucketState initialState = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			Duration expectedExpireIn = Duration.ofSeconds(1); // 1 token short of being full again

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(expectedExpireIn, result.getExpireIn());

		}

		@Test
		void resetAtShouldBeNowPlusExpireInWhenAllowed() {

			// Arrange
			TokenBucketState initialState = stateWith(3.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			Instant expectedResetAt = fixedNow.plusSeconds(3); // 3 tokens short of the cap

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(expectedResetAt, result.getResetAt());

		}

		@Test
		void resetAtShouldBeNowPlusRetryAfterWhenDenied() {

			// Arrange
			TokenBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);
			Instant expectedResetAt = fixedNow.plusSeconds(1);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

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
			TokenBucketState initialState = stateWith(3.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithm(initialState, context);

			// Assert
			assertNotSame(initialState, result.getState());

		}

		@Test
		void algorithmShouldNeverMutateOriginalState() {

			// Arrange
			TokenBucketState initialState = stateWith(3.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(5));
			double originalTokens = initialState.getTokens();
			Instant originalLastRefill = initialState.getLastRefill();

			// Act
			executeAlgorithm(initialState, context);

			// Assert
			assertEquals(originalTokens, initialState.getTokens());
			assertEquals(originalLastRefill, initialState.getLastRefill());

		}

	}

	@Nested
	class PolicyCases {

		@Test
		void customCapacityShouldBeRespected() {

			// Arrange
			TokenBucketPolicy customPolicy = policyWith(2.0, 1.0);
			TokenBucketState initialState = stateWith(2.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(1, decision.getRemaining());

		}

		@Test
		void customRefillRateShouldBeRespected() {

			// Arrange
			TokenBucketPolicy customPolicy = policyWith(5.0, 2.0); // 2 tokens/sec
			TokenBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(1));

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(1, decision.getRemaining());

		}

		@Test
		void slowRefillRateShouldBeRespected() {

			// Arrange
			TokenBucketPolicy customPolicy = policyWith(5.0, 0.5); // 0.5 tokens/sec
			TokenBucketState initialState = stateWith(0.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow.plusSeconds(2));

			// Act
			AlgorithmResult<TokenBucketState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

	}

	@Nested
	class SequentialExecutionCases {

		@Test
		void multipleAllowedRequestsShouldDecreaseTokensSequentially() {

			// Arrange
			TokenBucketState state = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act & Assert
			// Request 1
			AlgorithmResult<TokenBucketState> result1 = executeAlgorithm(state, context);
			AllowedDecision decision1 = extractAllowed(result1);
			assertEquals(4, decision1.getRemaining());
			state = result1.getState();

			// Request 2
			AlgorithmResult<TokenBucketState> result2 = executeAlgorithm(state, context);
			AllowedDecision decision2 = extractAllowed(result2);
			assertEquals(3, decision2.getRemaining());
			state = result2.getState();

			// Request 3
			AlgorithmResult<TokenBucketState> result3 = executeAlgorithm(state, context);
			AllowedDecision decision3 = extractAllowed(result3);
			assertEquals(2, decision3.getRemaining());

		}

		@Test
		void requestsShouldBeDeniedOnceBucketIsEmpty() {

			// Arrange
			TokenBucketState state = stateWith(5.0, fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act & Assert - Consume the 5 available tokens
			for (int i = 1; i <= 5; i++) {
				AlgorithmResult<TokenBucketState> result = executeAlgorithm(state, context);
				assertTrue(result.isAllowed(), "Request " + i + " should be allowed");
				state = result.getState();
			}

			// Request 6 - must be denied
			AlgorithmResult<TokenBucketState> deniedResult = executeAlgorithm(state, context);
			assertFalse(deniedResult.isAllowed(), "Request 6 should be denied");

		}

		@Test
		void bucketShouldRefillAfterBeingEmptied() {

			// Arrange
			TokenBucketState state = stateWith(0.0, fixedNow);
			AlgorithmContext emptyContext = contextAt(fixedNow);

			// Act - Request with no tokens available
			AlgorithmResult<TokenBucketState> deniedResult = executeAlgorithm(state, emptyContext);
			assertFalse(deniedResult.isAllowed(), "Should be denied when bucket is empty");
			state = deniedResult.getState();

			// Act - 5 seconds pass, 5 tokens are refilled
			AlgorithmContext refilledContext = contextAt(fixedNow.plusSeconds(5));
			AlgorithmResult<TokenBucketState> allowedResult = executeAlgorithm(state, refilledContext);

			// Assert
			AllowedDecision decision = extractAllowed(allowedResult);
			assertEquals(4, decision.getRemaining());

		}

	}

	@Nested
	class CodecCases {

		@Test
		void encodeThenDecodeShouldReturnEquivalentState() {

			// Arrange
			StateCodec<TokenBucketState> codec = algorithm.getCodec();
			TokenBucketState original = new TokenBucketState(2.5, fixedNow);

			// Act
			TokenBucketState decoded = codec.decode(codec.encode(original));

			// Assert
			assertEquals(original.getTokens(), decoded.getTokens(), 0.0001);
			assertEquals(original.getLastRefill(), decoded.getLastRefill());

		}

		@Test
		void encodeThenDecodeShouldWorkWithZeroTokens() {

			// Arrange
			StateCodec<TokenBucketState> codec = algorithm.getCodec();
			TokenBucketState original = new TokenBucketState(0.0, fixedNow);

			// Act
			TokenBucketState decoded = codec.decode(codec.encode(original));

			// Assert
			assertEquals(0.0, decoded.getTokens(), 0.0001);

		}

	}
	
}
