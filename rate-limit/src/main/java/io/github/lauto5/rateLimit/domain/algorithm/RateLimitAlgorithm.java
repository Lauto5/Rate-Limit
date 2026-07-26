package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmExecutionResult;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

public interface RateLimitAlgorithm <S extends AlgorithmState , P extends RateLimitPolicy> {

	public AlgorithmExecutionResult<S> execute(S state , P policy , AlgorithmContext context);
	
	public S createInitialState(P policy , AlgorithmContext context);
	
}
