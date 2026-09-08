package io.github.lauto5.rateLimit.domain.policies;

import java.time.Duration;

/**
 * Configuration for the GCRA algorithm.
 *
 * <p>Defines the average permitted {@code rate} (requests per unit of time) and the maximum
 * permitted burst expressed as a {@code Duration}.
 */
public final class GcraPolicy implements RateLimitPolicy {

	private final double rate; // average permitted rate
	private final Duration burst; // maximum permitted burst (in time)
	
	public GcraPolicy(double rate, Duration burst) {
		super();
		this.rate = rate;
		this.burst = burst;
	}
	
	public double getRate() {
		return rate;
	}
	
	public Duration getBurst() {
		return burst;
	}
	
}
