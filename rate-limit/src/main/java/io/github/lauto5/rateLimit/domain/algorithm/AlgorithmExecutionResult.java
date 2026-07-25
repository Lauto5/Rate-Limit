package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.domain.states.AlgorithmState;

public class AlgorithmExecutionResult <S extends AlgorithmState>{

	private final S state;
	
	private final RateLimitResult result;

	private final long ttl;

	public AlgorithmExecutionResult(S state, RateLimitResult result, long ttl) {
		super();
		this.state = state;
		this.result = result;
		this.ttl = ttl;
	}

	public S getState() {
		return state;
	}

	public RateLimitResult getResult() {
		return result;
	}

	public long getTtl() {
		return ttl;
	}
	
}
