package io.github.lauto5.rateLimit.domain.model;

import java.time.Duration;

import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public final class AlgorithmExecutionResult <S extends AlgorithmState>{

	private final S state;
	
	private final RateLimitResult result;

	private final Duration expireIn;

	public AlgorithmExecutionResult(S state, RateLimitResult result, Duration expireIn) {
		super();
		this.state = state;
		this.result = result;
		this.expireIn = expireIn;
	}

	public S getState() {
		return state;
	}

	public RateLimitResult getResult() {
		return result;
	}

	public Duration getExpireIn() {
		return expireIn;
	}
	
}
