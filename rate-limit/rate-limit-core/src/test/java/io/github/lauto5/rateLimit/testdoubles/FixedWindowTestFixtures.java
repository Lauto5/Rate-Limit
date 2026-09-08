package io.github.lauto5.rateLimit.testdoubles;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;

/**
 * Shared {@code FixedWindow} fixtures for the core module tests.
 *
 * <p>Centralizes the fixed timestamp and the state/result builders that were duplicated
 * across the application tests, so the tested values stay consistent from a single place.
 */
public final class FixedWindowTestFixtures {

	public static final Duration ONE_MINUTE =
			Duration.ofMinutes(1);

	public static final Duration RETRY_AFTER =
			Duration.ofSeconds(30);

	private FixedWindowTestFixtures() {
	}

	public static FixedWindowState stateWith(int count) {
		return new FixedWindowState(count, TestTime.FIXED_NOW);
	}

	public static FixedWindowState stateWith(int count, Instant windowStart) {
		return new FixedWindowState(count, windowStart);
	}

	public static AlgorithmResult<FixedWindowState> allowedResult(
			FixedWindowState state,
			int remaining,
			Instant resetAt,
			Duration expiresIn) {

		return AlgorithmResult.allowed(
				state,
				remaining,
				resetAt,
				expiresIn
		);
	}

	public static AlgorithmResult<FixedWindowState> deniedResult(
			FixedWindowState state,
			Duration retryAfter,
			Instant resetAt,
			Duration expiresIn) {

		return AlgorithmResult.denied(
				state,
				retryAfter,
				resetAt,
				expiresIn
		);
	}

	public static AtomicOperationResult<FixedWindowState> operationResult(
			Instant expiresAt,
			AlgorithmResult<FixedWindowState> algorithmResult) {

		return new AtomicOperationResult<>(
				expiresAt,
				algorithmResult
		);
	}
}