package io.github.lauto5.rateLimit.domain.algorithm;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.domain.algorithmState.GcraState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.GcraPolicy;

/**
 * Default implementation of {@link GcraAlgorithm}.
 *
 * <p>The algorithm tracks a theoretical arrival time (TAT) and permits a request whenever the
 * current instant is no earlier than {@code TAT - burst}. Each allowed request advances the
 * TAT by a fixed emission interval derived from the average rate. State is serialized as a
 * UTF-8 string containing the TAT in epoch milliseconds.
 */
public final class GcraAlgorithmImpl implements GcraAlgorithm {

	private static final StateCodec<GcraState> CODEC = new StateCodec<GcraState>() {

		@Override
		public byte[] encode(GcraState state) {
			return String.valueOf(state.getTat()).getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public GcraState decode(byte[] data) {

			long tat = Long.parseLong(new String(data, StandardCharsets.UTF_8));

			return new GcraState(tat);
		}

	};

	@Override
	public StateCodec<GcraState> getCodec() {
		return CODEC;
	}
	
	@Override
	public AlgorithmResult<GcraState> execute(
			GcraState state,
			GcraPolicy policy,
			AlgorithmContext context) {

		Instant now = context.getNow();

		long nowMillis = now.toEpochMilli();

		long emissionIntervalMillis =
				computeEmissionIntervalMillis(policy);

		long toleranceMillis =
				policy.getBurst().toMillis();

		long tat =
				state.getTat();

		/*
		 * If there is no active debt, the effective TAT
		 * begins at the current instant.
		 */
		long effectiveTat =
				Math.max(tat, nowMillis);

		/*
		 * Earliest instant at which a new request
		 * can be accepted.
		 */
		long allowAtMillis =
				effectiveTat - toleranceMillis;

		// ============================================================
		// DENIED
		// ============================================================

		if (nowMillis < allowAtMillis) {

			Duration retryAfter =
					Duration.ofMillis(
							allowAtMillis - nowMillis
					);

			Duration expireIn =
					Duration.ofMillis(
							Math.max(
									tat - nowMillis,
									0
							)
					);

			return AlgorithmResult.denied(
					state,
					retryAfter,
					Instant.ofEpochMilli(allowAtMillis),
					expireIn
			);
		}

		// ============================================================
		// ALLOWED
		// ============================================================

		long newTat =
				effectiveTat + emissionIntervalMillis;

		GcraState newState =
				new GcraState(newTat);

		int remaining =
				calculateRemaining(
						newTat,
						nowMillis,
						toleranceMillis,
						emissionIntervalMillis
				);

		Instant resetAt =
				Instant.ofEpochMilli(newTat);

		Duration expireIn =
				Duration.ofMillis(
						Math.max(
								newTat - nowMillis,
								0
						)
				);

		return AlgorithmResult.allowed(
				newState,
				remaining,
				resetAt,
				expireIn
		);
	}

	@Override
	public GcraState createInitialState(
			GcraPolicy policy,
			AlgorithmContext context) {

		return new GcraState(
				context.getNow().toEpochMilli()
		);
	}

	private long computeEmissionIntervalMillis(
			GcraPolicy policy) {

		long interval =
				Math.round(
						1000.0 / policy.getRate()
				);

		return Math.max(interval, 1);
	}

	private int calculateRemaining(
			long tat,
			long nowMillis,
			long toleranceMillis,
			long emissionIntervalMillis) {

		long debtMillis =
				Math.max(
						tat - nowMillis,
						0
				);

		long availableTolerance =
				Math.max(
						toleranceMillis - debtMillis,
						0
				);

		return (int) (
				availableTolerance / emissionIntervalMillis
		);
	}
}
