package io.github.lauto5.rateLimit.domain.policies;

public final class TokenBucketPolicy implements RateLimitPolicy {

	private final double capacity;
	private final double refillRate; // Tokens per unit of time

	public TokenBucketPolicy(double capacity, double refillRate) {
		super();

		validateCapacity(capacity);
		validateRefillRate(refillRate);

		this.capacity = capacity;
		this.refillRate = refillRate;
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

	private void validateRefillRate(double refillRate) {

		if (Double.isNaN(refillRate)) {
			throw new IllegalArgumentException("RefillRate cannot be NaN");
		}

		if (Double.isInfinite(refillRate)) {
			throw new IllegalArgumentException("RefillRate must be finite, got: " + refillRate);
		}

		if (refillRate <= 0) {
			throw new IllegalArgumentException("RefillRate must be greater than 0, got: " + refillRate);
		}
	}

	public double getCapacity() {
		return capacity;
	}

	public double getRefillRate() {
		return refillRate;
	}

}
