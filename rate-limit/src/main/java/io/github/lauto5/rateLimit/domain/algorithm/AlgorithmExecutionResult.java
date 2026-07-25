package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Duration;

import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.domain.states.AlgorithmState;

public class AlgorithmExecutionResult <S extends AlgorithmState>{

	private final S state;
	
	private final RateLimitResult result;

	private final Duration ttl;

	public AlgorithmExecutionResult(S state, RateLimitResult result, Duration ttl) {
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

	public Duration getTtl() {
		return ttl;
	}
	
}
