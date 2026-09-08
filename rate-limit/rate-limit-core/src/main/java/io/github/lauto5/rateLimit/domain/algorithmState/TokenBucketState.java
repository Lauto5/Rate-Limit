package io.github.lauto5.rateLimit.domain.algorithmState;

import java.time.Instant;

/**
 * Serializable state for the token bucket algorithm.
 *
 * <p>Holds the current number of tokens available in the bucket and the instant at which the
 * bucket was last refilled.
 */
public final class TokenBucketState implements AlgorithmState {

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
