package io.github.lauto5.rateLimit.application.ports.out;

import java.time.Instant;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

/**
 * Represents an operation that applies a rate-limit evaluation against a stored state.
 *
 * <p>An {@code AtomicOperation} is executed by a {@link RateLimitStore} inside a single atomic
 * context. It exposes the current instant and the {@link StateCodec} required to (de)serialize
 * the state, so that the store can persist the result safely.
 *
 * @param <S> the concrete algorithm state type
 */
public interface AtomicOperation <S extends AlgorithmState>{

	/**
	 * Applies the operation against the currently stored state and produces the result.
	 *
	 * @param currentStoreState the state currently stored, or {@code null} if none exists yet
	 * @return the outcome of the operation, including the new state and its expiry
	 */
	public AtomicOperationResult<S> apply(StoreState<S> currentStoreState);
	
	/**
	 * @return the current instant used as the reference time for this operation
	 */
    public Instant getNow();
    
    /**
     * @return the codec used to encode and decode instances of the state {@code S}
     */
    public StateCodec<S> getCodec();
}
