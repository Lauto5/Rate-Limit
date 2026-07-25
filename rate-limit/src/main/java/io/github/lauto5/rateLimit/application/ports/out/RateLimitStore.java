package io.github.lauto5.rateLimit.application.ports.out;

import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;
import io.github.lauto5.rateLimit.domain.states.AlgorithmState;

public interface RateLimitStore {

	public <S extends AlgorithmState, P extends RateLimitPolicy , R> R executeAtomically(String identifier, P policy , AtomicOperation<S , R> operation);
	
}
