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

		validateRate(rate);
		validateBurst(burst);

		this.rate = rate;
		this.burst = burst;
	}

	private void validateRate(double rate) {

		if (Double.isNaN(rate)) {
			throw new IllegalArgumentException("Rate cannot be NaN");
		}

		if (Double.isInfinite(rate)) {
			throw new IllegalArgumentException("Rate must be finite, got: " + rate);
		}

		if (rate <= 0) {
			throw new IllegalArgumentException("Rate must be greater than 0, got: " + rate);
		}
	}

	private void validateBurst(Duration burst) {

		if (burst == null) {
			throw new IllegalArgumentException("Burst cannot be null");
		}

		if (burst.isNegative()) {
			throw new IllegalArgumentException("Burst cannot be negative, got: " + burst);
		}
	}
	
	public double getRate() {
		return rate;
	}
	
	public Duration getBurst() {
		return burst;
	}
	
}
