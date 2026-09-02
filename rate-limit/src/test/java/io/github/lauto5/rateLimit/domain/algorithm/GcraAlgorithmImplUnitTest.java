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
		standardPolicy = new GcraPolicy(1.0, Duration.ofSeconds(5)); // 1 req/seg, ráfaga de 5s
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
			GcraState initialState = stateWithTat(fixedNow); // tat == now -> balde "vacio"
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
			// tat 6s en el futuro; con tolerancia de 5s, "now" todavia
			// esta 1s antes del instante minimo permitido.
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
			// allowAt = tat - tolerance = (now+5000) - 5000 = now exacto
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
			// tat ya adelantado 5s (todo el margen de rafaga ya "reservado")
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
			// Si el TAT quedo en el pasado (balde inactivo por mucho tiempo),
			// el effectiveTat debe recalcularse a partir de "now", no del
			// TAT viejo.
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
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(8)); // supera la tolerancia de 5s
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
			GcraPolicy customPolicy = policyWith(2.0, Duration.ofSeconds(5)); // 2 req/seg -> intervalo 500ms
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
			GcraPolicy customPolicy = policyWith(1.0, Duration.ofSeconds(2)); // rafaga chica
			GcraState initialState = stateWithTat(fixedNow.plusSeconds(3)); // fuera de la tolerancia de 2s
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
			// 1000/10000 = 0.1ms -> redondea a 0 -> debe forzarse a 1ms
			// para evitar division por cero al calcular "remaining".
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
			// Documenta el comportamiento real: al arrancar con tat==now,
			// la primera request es "gratis" y luego el margen de tolerancia
			// (5000ms / 1000ms por request) permite 5 mas -> 6 en total.
			GcraState state = stateWithTat(fixedNow);
			AlgorithmContext context = contextAt(fixedNow); // el reloj no avanza entre llamadas

			int[] expectedRemaining = { 4, 3, 2, 1, 0, 0 };

			// Act & Assert
			for (int i = 0; i < expectedRemaining.length; i++) {

				AlgorithmResult<GcraState> result = executeAlgorithm(state, context);
				AllowedDecision decision = extractAllowed(result);

				assertEquals(expectedRemaining[i], decision.getRemaining(),
						"Request " + (i + 1) + " remaining mismatch");

				state = result.getState();

			}

			// La septima request ya debe ser denegada
			AlgorithmResult<GcraState> deniedResult = executeAlgorithm(state, context);
			assertFalse(deniedResult.isAllowed(), "7th request should be denied");

		}

		@Test
		void shouldAllowAgainOnceEnoughTimeHasPassed() {

			// Arrange
			GcraState state = stateWithTat(fixedNow.plusSeconds(6)); // fuera de tolerancia
			AlgorithmContext deniedContext = contextAt(fixedNow);

			AlgorithmResult<GcraState> deniedResult = executeAlgorithm(state, deniedContext);
			DeniedDecision decision = extractDenied(deniedResult);
			Duration retryAfter = decision.getRetryAfter();

			// Act - Avanzamos exactamente lo que indico retryAfter
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
