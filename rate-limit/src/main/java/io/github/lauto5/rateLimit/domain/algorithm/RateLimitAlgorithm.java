package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;
import io.github.lauto5.rateLimit.domain.states.AlgorithmState;

public interface RateLimitAlgorithm <S extends AlgorithmState , P extends RateLimitPolicy> {

	public AlgorithmExecutionResult<S> execute(S state);
	
	public S initState(P policy);
	
}
