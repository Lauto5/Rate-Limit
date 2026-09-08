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

import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.GcraState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.model.AllowedDecision;
import io.github.lauto5.rateLimit.domain.model.DeniedDecision;
import io.github.lauto5.rateLimit.domain.policies.GcraPolicy;

public class GcraAlgorithmImplUnitTest {

	private GcraAlgorithm algorithm;
	private GcraPolicy standardPolicy;
	private Instant fixedNow;

	@BeforeEach
	void setUp() {
		algorithm = new GcraAlgorithmImpl();
		standardPolicy = new GcraPolicy(1.0, Duration.ofSeconds(5)); // 1 req/sec, 5s burst
		fixedNow = Instant.parse("2026-01-01T10:00:00Z");
	}

	// ==================== HELPER ====================

	private AlgorithmContext contextAt(Instant instant) {
		return new AlgorithmContext(instant);
	}

	private GcraState stateWithTat(Instant tat) {
		return new GcraState(tat.toEpochMilli());
	}

	private GcraPolicy policyWith(double rate, Duration burst) {
		return new GcraPolicy(rate, burst);
	}

	private AlgorithmResult<GcraState> executeAlgorithm(GcraState state, AlgorithmContext context) {
		return algorithm.execute(state, standardPolicy, context);
	}

	private AlgorithmResult<GcraState> executeAlgorithmWithPolicy(GcraState state, GcraPolicy policy,
			AlgorithmContext context) {
		return algorithm.execute(state, policy, context);
	}

	private AllowedDecision extractAllowed(AlgorithmResult<GcraState> result) {
		assertTrue(result.isAllowed(), "Expected decision to be ALLOWED");
		return assertInstanceOf(AllowedDecision.class, result.getDecision());
	}

	private DeniedDecision extractDenied(AlgorithmResult<GcraState> result) {
		assertFalse(result.isAllowed(), "Expected decision to be DENIED");
		return assertInstanceOf(DeniedDecision.class, result.getDecision());
	}

	// ==================== HELPER ====================

	// ==================== TESTS ====================

	@Nested
	class BasicCases {

		@Test
		void firstRequestOnFreshStateShouldBeAllowed() {

			// Arrange
			GcraState initialState = stateWithTat(fixedNow); // tat == now -> "empty" bucket
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			GcraState newState = result.getState();

			assertEquals(fixedNow.plusSeconds(1).toEpochMilli(), newState.getTat());
			assertEquals(4, decision.getRemaining());
			assertEquals(fixedNow.plusSeconds(1), result.getResetAt());
			assertEquals(Duration.ofSeconds(1), result.getExpireIn());
			assertNotSame(initialState, newState);

		}

		@Test
		void requestBeyondBurstToleranceShouldBeDenied() {

			// Arrange
			// tat 6s in the future; with a 5s tolerance, "now" is still
			// 1s before the minimum allowed instant.
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(6));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);

			assertEquals(Duration.ofSeconds(1), decision.getRetryAfter());
			assertEquals(fixedNow.plusSeconds(1), result.getResetAt());
			assertEquals(Duration.ofSeconds(6), result.getExpireIn());

		}

	}

	@Nested
	class BoundaryCases {

		@Test
		void requestExactlyAtAllowedInstantShouldBeAllowed() {

			// Arrange
			// allowAt = tat - tolerance = (now+5000) - 5000 = exact now
			GcraState initialState = stateWithTat(fixedNow.plusMillis(5000));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());
			assertEquals(fixedNow.plusSeconds(6), result.getResetAt());

		}

		@Test
		void requestOneMillisecondBeforeAllowedInstantShouldBeDenied() {

			// Arrange
			GcraState initialState = stateWithTat(fixedNow.plusMillis(5001));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			DeniedDecision decision = extractDenied(result);
			assertEquals(Duration.ofMillis(1), decision.getRetryAfter());

		}

	}

	@Nested
	class RemainingCases {

		@Test
		void remainingShouldBeZeroWhenToleranceFullyConsumed() {

			// Arrange
			// tat already 5s ahead (all the burst margin already "reserved")
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(5));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(0, decision.getRemaining());

		}

	}

	@Nested
	class TatCases {

		@Test
		void allowedRequestShouldAdvanceTatByOneEmissionInterval() {

			// Arrange
			GcraState initialState = stateWithTat(fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(fixedNow.plusSeconds(1).toEpochMilli(), result.getState().getTat());

		}
		
		@Test
		void expiredTatShouldBeRebasedToCurrentTime() {

			// Arrange
			// If the TAT stayed in the past (bucket idle for a long time),
			// the effectiveTat must be recomputed from "now", not from the
			// stale TAT.
			GcraState initialState = stateWithTat(fixedNow.minusSeconds(10));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(fixedNow.plusSeconds(1).toEpochMilli(), result.getState().getTat());

		}

		@Test
		void deniedRequestShouldReturnTheExactSameStateInstance() {

			// Arrange
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(6));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertSame(initialState, result.getState(), "Denied requests must not create a new state");
			assertEquals(fixedNow.plusSeconds(6).toEpochMilli(), result.getState().getTat(),
					"TAT must remain untouched when denied");

		}

	}

	@Nested
	class TimeCases {

		@Test
		void resetAtWhenDeniedShouldBeTheExactAllowedInstant() {

			// Arrange
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(8)); // exceeds the 5s tolerance
			AlgorithmContext context = contextAt(fixedNow);
			Instant expectedResetAt = fixedNow.plusSeconds(3); // allowAt = (now+8s) - 5s

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertEquals(expectedResetAt, result.getResetAt());

		}

		@Test
		void expireInWhenDeniedShouldUseRawTatNotEffectiveTat() {

			// Arrange
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(8));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			extractDenied(result);
			assertEquals(Duration.ofSeconds(8), result.getExpireIn());

		}

		@Test
		void resetAtWhenAllowedShouldBeExactlyTheNewTat() {

			// Arrange
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(2));
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			extractAllowed(result);
			assertEquals(fixedNow.plusSeconds(3), result.getResetAt());

		}

	}

	@Nested
	class ImmutabilityCases {

		@Test
		void allowedRequestShouldCreateNewStateInstance() {

			// Arrange
			GcraState initialState = stateWithTat(fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithm(initialState, context);

			// Assert
			assertNotSame(initialState, result.getState());

		}

	}

	@Nested
	class InitialStateCases {

		@Test
		void createInitialStateShouldSetTatToCurrentTime() {

			// Arrange
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			GcraState initialState = algorithm.createInitialState(standardPolicy, context);

			// Assert
			assertEquals(fixedNow.toEpochMilli(), initialState.getTat());

		}

	}

	@Nested
	class PolicyCases {

		@Test
		void shouldRespectCustomRate() {

			// Arrange
			GcraPolicy customPolicy = policyWith(2.0, Duration.ofSeconds(5)); // 2 req/sec -> 500ms interval
			GcraState initialState = stateWithTat(fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			extractAllowed(result);
			assertEquals(fixedNow.plusMillis(500).toEpochMilli(), result.getState().getTat());

		}

		@Test
		void shouldRespectCustomBurst() {

			// Arrange
			GcraPolicy customPolicy = policyWith(1.0, Duration.ofSeconds(2)); // small burst
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(3)); // outside the 2s tolerance
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithmWithPolicy(initialState, customPolicy, context);

			// Assert
			DeniedDecision decision = extractDenied(result);
			assertEquals(Duration.ofSeconds(1), decision.getRetryAfter());

		}

		@Test
		void extremelyHighRateShouldClampEmissionIntervalToOneMillisecond() {

			// Arrange
			// 1000/10000 = 0.1ms -> rounds to 0 -> must be clamped to 1ms
			// to avoid division by zero when computing "remaining".
			GcraPolicy extremePolicy = policyWith(10000.0, Duration.ofMillis(10));
			GcraState initialState = stateWithTat(fixedNow);
			AlgorithmContext context = contextAt(fixedNow);

			// Act
			AlgorithmResult<GcraState> result = executeAlgorithmWithPolicy(initialState, extremePolicy, context);

			// Assert
			AllowedDecision decision = extractAllowed(result);
			assertEquals(fixedNow.plusMillis(1).toEpochMilli(), result.getState().getTat());
			assertEquals(9, decision.getRemaining());

		}

	}

	@Nested
	class SequentialExecutionCases {

		@Test
		void freshBucketShouldAllowBurstPlusOneBeforeDenying() {

			// Arrange
			// Documents the real behavior: starting from tat==now,
			// the first request is "free" and then the tolerance margin
			// (5000ms / 1000ms per request) allows 5 more -> 6 in total.
			GcraState state = stateWithTat(fixedNow);
			AlgorithmContext context = contextAt(fixedNow); // the clock does not advance between calls

			int[] expectedRemaining = { 4, 3, 2, 1, 0, 0 };

			// Act & Assert
			for (int i = 0; i < expectedRemaining.length; i++) {

				AlgorithmResult<GcraState> result = executeAlgorithm(state, context);
				AllowedDecision decision = extractAllowed(result);

				assertEquals(expectedRemaining[i], decision.getRemaining(),
						"Request " + (i + 1) + " remaining mismatch");

				state = result.getState();

			}

			// The seventh request must already be denied
			AlgorithmResult<GcraState> deniedResult = executeAlgorithm(state, context);
			assertFalse(deniedResult.isAllowed(), "7th request should be denied");

		}

		@Test
		void shouldAllowAgainOnceEnoughTimeHasPassed() {

			// Arrange
			GcraState state = stateWithTat(fixedNow.plusSeconds(6)); // outside tolerance
			AlgorithmContext deniedContext = contextAt(fixedNow);

			AlgorithmResult<GcraState> deniedResult = executeAlgorithm(state, deniedContext);
			DeniedDecision decision = extractDenied(deniedResult);
			Duration retryAfter = decision.getRetryAfter();

			// Act - advance exactly what retryAfter indicated
			AlgorithmContext retryContext = contextAt(fixedNow.plus(retryAfter));
			AlgorithmResult<GcraState> retryResult = executeAlgorithm(state, retryContext);

			// Assert
			assertTrue(retryResult.isAllowed(), "Should be allowed exactly at retryAfter");

		}

	}
	
	@Nested
	class CodecCases {

		@Test
		void encodeThenDecodeShouldReturnEquivalentState() {

			// Arrange
			StateCodec<GcraState> codec = algorithm.getCodec();
			GcraState original = new GcraState(fixedNow.plusSeconds(5).toEpochMilli());

			// Act
			GcraState decoded = codec.decode(codec.encode(original));

			// Assert
			assertEquals(original.getTat(), decoded.getTat());

		}

		@Test
		void encodeThenDecodeShouldWorkWithZeroTat() {

			// Arrange
			StateCodec<GcraState> codec = algorithm.getCodec();
			GcraState original = new GcraState(0L);

			// Act
			GcraState decoded = codec.decode(codec.encode(original));

			// Assert
			assertEquals(0L, decoded.getTat());

		}

	}
	

}
