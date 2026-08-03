package io.github.lauto5.rateLimit.application.ports.out;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

public interface RateLimitStore {

	public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(String identifier , AtomicOperation<S> operation);
	
}
