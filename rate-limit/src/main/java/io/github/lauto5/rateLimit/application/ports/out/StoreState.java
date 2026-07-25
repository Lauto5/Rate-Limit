package io.github.lauto5.rateLimit.application.ports.out;

import io.github.lauto5.rateLimit.domain.states.AlgorithmState;

public class StoreState<T extends AlgorithmState> {

	private final T state;

	private final long TTL;

	private final long updateAt;

	public StoreState(T state, long tTL, long updateAt) {
		super();
		this.state = state;
		TTL = tTL;
		this.updateAt = updateAt;
	}

	public T getState() {
		return state;
	}

	public long getTTL() {
		return TTL;
	}

	public long getUpdateAt() {
		return updateAt;
	}

}
