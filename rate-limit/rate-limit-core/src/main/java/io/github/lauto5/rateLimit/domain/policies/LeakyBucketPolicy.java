package io.github.lauto5.rateLimit.domain.policies;

/**
 * Configuration for the leaky bucket algorithm.
 *
 * <p>Defines the maximum {@code capacity} of the bucket and the {@code leakRate} at which
 * water drains per unit of time.
 */
public final class LeakyBucketPolicy implements RateLimitPolicy {

	private final double capacity;
	private final double leakRate; // requests processed per unit of time
	
	public LeakyBucketPolicy(double capacity, double leakRate) {
		super();
		this.capacity = capacity;
		this.leakRate = leakRate;
	}
	
	public double getCapacity() {
		return capacity;
	}
	
	public double getLeakRate() {
		return leakRate;
	}
	
	
	
	
}
