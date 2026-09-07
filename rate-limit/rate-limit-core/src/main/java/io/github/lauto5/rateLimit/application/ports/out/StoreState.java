package io.github.lauto5.rateLimit.application.ports.out;

import java.time.Instant;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

/**
 * Immutable holder pairing an algorithm state with the instant at which it expires.
 *
 * <p>A {@code StoreState} is the unit persisted by a {@link RateLimitStore}. It wraps the
 * current algorithm state {@code T} together with an {@link Instant} at which that state
 * should be considered expired.
 *
 * @param <T> the concrete algorithm state type
 */
public final class StoreState<T extends AlgorithmState> {

	private final T state;

	private final Instant expiresAt;

	/**
	 * Creates a stored state.
	 *
	 * @param state     the algorithm state
	 * @param expiresAt the instant at which the state expires
	 */
	public StoreState(T state, Instant expiresAt) {
		super();
		this.state = state;
		this.expiresAt = expiresAt;
	}

	/**
	 * @return the underlying algorithm state
	 */
	public T getState() {
		return state;
	}

	/**
	 * @return the instant at which the state expires
	 */
	public Instant getExpiresAt() {
		return expiresAt;
	}
	
	/**
	 * Determines whether this state has already expired at the given instant.
	 *
	 * @param now the instant to compare against
	 * @return {@code true} if {@code now} is on or after the expiry instant
	 */
	public boolean isExpired(Instant now) {
	    return !now.isBefore(expiresAt);
	}

}
