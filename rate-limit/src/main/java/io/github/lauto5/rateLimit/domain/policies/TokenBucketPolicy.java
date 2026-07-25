package io.github.lauto5.rateLimit.domain.policies;

public class TokenBucketPolicy implements RateLimitPolicy {

	private final double capacity;
	private final double refillRate; // Tokens per unit of time
	
	public TokenBucketPolicy(double capacity, double refillRate) {
		super();
		this.capacity = capacity;
		this.refillRate = refillRate;
	}
	public double getCapacity() {
		return capacity;
	}
	public double getRefillRate() {
		return refillRate;
	}
	
	
	
}
