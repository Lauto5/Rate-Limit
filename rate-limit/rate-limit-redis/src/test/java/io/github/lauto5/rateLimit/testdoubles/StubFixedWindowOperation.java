package io.github.lauto5.rateLimit.testdoubles;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;

/**
 * Stub {@link AtomicOperation} with fully controlled clock, expiry and decision, for
 * testing store behavior (TTL calculation, retries, interruptions) without depending on a
 * concrete algorithm. The resulting state is always a {@link FixedWindowState} carrying the
 * given count.
 */
public final class StubFixedWindowOperation implements AtomicOperation<FixedWindowState> {

	private final Instant now;
	private final Instant expiresAt;
	private final boolean allowed;
	private final FixedWindowState state;

	public StubFixedWindowOperation(Instant now, Instant expiresAt, boolean allowed, FixedWindowState state) {
		this.now = now;
		this.expiresAt = expiresAt;
		this.allowed = allowed;
		this.state = state;
	}

	@Override
	public AtomicOperationResult<FixedWindowState> apply(StoreState<FixedWindowState> currentStoreState) {

		Duration expireIn = Duration.between(now, expiresAt);

		AlgorithmResult<FixedWindowState> algorithmResult = allowed
				? AlgorithmResult.allowed(state, 1, expiresAt, expireIn)
				: AlgorithmResult.denied(state, Duration.ZERO, expiresAt, expireIn);

		return new AtomicOperationResult<>(expiresAt, algorithmResult);
	}

	@Override
	public Instant getNow() {
		return now;
	}

	@Override
	public StateCodec<FixedWindowState> getCodec() {
		return new FixedWindowAlgorithmImpl().getCodec();
	}

}