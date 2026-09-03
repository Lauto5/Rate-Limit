package io.github.lauto5.rateLimit.application.ports.out;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

/**
 * Outbound port that persists and atomically updates rate-limit state.
 *
 * <p>A {@code RateLimitStore} stores the algorithm state associated with each identifier and
 * executes {@link AtomicOperation}s atomically, guaranteeing that concurrent executions for the
 * same identifier are serialized. Implementations include in-memory and Redis-backed stores.
 */
public interface RateLimitStore {

	/**
	 * Executes the given operation atomically for the specified identifier.
	 *
	 * @param identifier the key identifying the request stream whose state is being updated
	 * @param operation  the operation to apply to the currently stored state
	 * @param <S>        the concrete algorithm state type
	 * @return the result of the atomic operation
	 */
	public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(String identifier , AtomicOperation<S> operation);
	
}
