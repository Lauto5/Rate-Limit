package io.github.lauto5.rateLimit.domain.states;

public class TokenBucketState implements AlgorithmState {

	private final double tokens;
	
	private final double lastRefill;
	
	public TokenBucketState(double tokens, double lastRefill) {
		super();
		this.tokens = tokens;
		this.lastRefill = lastRefill;
	}
	
	public double getTokens() {
		return tokens;
	}
	
	public double getLastRefill() {
		return lastRefill;
	}
	
	
	
}
