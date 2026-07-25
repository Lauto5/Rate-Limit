package io.github.lauto5.rateLimit.application.ports.out;

import java.time.Instant;

import io.github.lauto5.rateLimit.domain.states.AlgorithmState;

public final class StoreState<T extends AlgorithmState> {

	private final T state;

	private final Instant expiresAt;

	public StoreState(T state, Instant expiresAt) {
		super();
		this.state = state;
		this.expiresAt = expiresAt;
	}

	public T getState() {
		return state;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
