package io.github.lauto5.rateLimit.application.ports.out;

import java.time.Instant;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;

/**
 * Immutable outcome of an {@link AtomicOperation}.
 *
 * <p>An {@code AtomicOperationResult} combines the instant at which the associated state
 * expires with the underlying {@link AlgorithmResult}. It provides convenience accessors to
 * obtain the resulting state or to build a {@link StoreState} ready for persistence.
 *
 * @param <S> the concrete algorithm state type
 */
public final class AtomicOperationResult<S extends AlgorithmState> {

    private final Instant expiresAt;

    private final AlgorithmResult<S> algorithmResult;

	/**
	 * Creates an atomic-operation result.
	 *
	 * @param expiresAt       the instant at which the resulting state should expire
	 * @param algorithmResult the algorithmic outcome, including the new state
	 */
	public AtomicOperationResult(Instant expiresAt, AlgorithmResult<S> algorithmResult) {
		super();
		this.expiresAt = expiresAt;
		this.algorithmResult = algorithmResult;
	}

	/**
	 * @return the instant at which the resulting state should expire
	 */
	public Instant getExpiresAt() {
		return expiresAt;
	}

	/**
	 * @return the underlying algorithmic result
	 */
	public AlgorithmResult<S> getAlgorithmResult() {
		return algorithmResult;
	}
    
    /**
     * @return the new state produced by the operation
     */
    public S getState() {
    	return this.algorithmResult.getState();
    }
    
    /**
     * Builds a {@link StoreState} combining the resulting state with its expiry instant.
     *
     * @return the store-ready state
     */
    public StoreState<S> getStoreState(){
    	return new StoreState<>(this.getState(), this.getExpiresAt()); 
    }
}
