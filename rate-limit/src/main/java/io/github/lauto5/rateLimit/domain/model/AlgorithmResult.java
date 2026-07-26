package io.github.lauto5.rateLimit.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public final class AlgorithmResult <S extends AlgorithmState>{
	
	private final S state;
	
    private final AlgorithmDecision decision;

    private final Instant resetAt;

	private final Duration expireIn;

	public AlgorithmResult(S state, AlgorithmDecision decision, Instant resetAt, Duration expireIn) {
		super();
		this.state = state;
		this.decision = decision;
		this.resetAt = resetAt;
		this.expireIn = expireIn;
	}
	
	public static <S extends AlgorithmState> AlgorithmResult<S> allowed(
	        S state,
	        long remaining,
	        Instant resetAt,
	        Duration expiresIn) {

	    return new AlgorithmResult<>(
	            state,
	            new AllowedDecision(remaining),
	            resetAt,
	            expiresIn
	    );
	}
	
	public static <S extends AlgorithmState> AlgorithmResult<S> denied(
	        S state,
	        Duration retryAfter,
	        Instant resetAt,
	        Duration expiresIn) {

	    return new AlgorithmResult<>(
	            state,
	            new DeniedDecision(retryAfter),
	            resetAt,
	            expiresIn
	    );
	}
	

	public S getState() {
		return state;
	}

	public AlgorithmDecision getDecision() {
		return decision;
	}

	public Instant getResetAt() {
		return resetAt;
	}

	public Duration getExpireIn() {
		return expireIn;
	}
	
}
