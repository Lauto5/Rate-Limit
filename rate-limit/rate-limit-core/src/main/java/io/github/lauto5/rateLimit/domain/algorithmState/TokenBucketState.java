package io.github.lauto5.rateLimit.domain.algorithmState;

import java.time.Instant;

public class TokenBucketState implements AlgorithmState {

	private final double tokens;
	
    private final Instant lastRefill;
	
	public TokenBucketState(double tokens, Instant lastRefill) {
		super();
		this.tokens = tokens;
		this.lastRefill = lastRefill;
	}

	public double getTokens() {
		return tokens;
	}

	public Instant getLastRefill() {
		return lastRefill;
	}

}
