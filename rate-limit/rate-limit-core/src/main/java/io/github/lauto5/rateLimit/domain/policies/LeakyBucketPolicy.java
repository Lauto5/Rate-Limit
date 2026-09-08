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

		validateCapacity(capacity);
		validateLeakRate(leakRate);

		this.capacity = capacity;
		this.leakRate = leakRate;
	}

	private void validateCapacity(double capacity) {

		if (Double.isNaN(capacity)) {
			throw new IllegalArgumentException("Capacity cannot be NaN");
		}

		if (Double.isInfinite(capacity)) {
			throw new IllegalArgumentException("Capacity must be finite, got: " + capacity);
		}

		if (capacity <= 0) {
			throw new IllegalArgumentException("Capacity must be greater than 0, got: " + capacity);
		}
	}

	private void validateLeakRate(double leakRate) {

		if (Double.isNaN(leakRate)) {
			throw new IllegalArgumentException("LeakRate cannot be NaN");
		}

		if (Double.isInfinite(leakRate)) {
			throw new IllegalArgumentException("LeakRate must be finite, got: " + leakRate);
		}

		if (leakRate <= 0) {
			throw new IllegalArgumentException("LeakRate must be greater than 0, got: " + leakRate);
		}
	}

	public double getCapacity() {
		return capacity;
	}

	public double getLeakRate() {
		return leakRate;
	}




}
