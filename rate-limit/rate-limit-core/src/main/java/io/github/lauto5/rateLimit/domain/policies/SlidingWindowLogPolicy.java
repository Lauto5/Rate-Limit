package io.github.lauto5.rateLimit.domain.policies;

import java.time.Duration;

/**
 * Configuration for the sliding window log algorithm.
 *
 * <p>Defines the maximum number of requests ({@code limit}) permitted within a sliding window
 * of the given {@code windowSize}.
 */
public final class SlidingWindowLogPolicy implements RateLimitPolicy {

	private final int limit;
	private final Duration windowSize;
	
	public SlidingWindowLogPolicy(int limit, Duration windowSize) {
		super();

		validateLimit(limit);
		validateWindowSize(windowSize);

		this.limit = limit;
		this.windowSize = windowSize;
	}

	private void validateLimit(int limit) {

		if (limit <= 0) {
			throw new IllegalArgumentException("Limit must be greater than 0, got: " + limit);
		}
	}

	private void validateWindowSize(Duration windowSize) {

		if (windowSize == null) {
			throw new IllegalArgumentException("WindowSize cannot be null");
		}

		if (windowSize.isNegative() || windowSize.isZero()) {
			throw new IllegalArgumentException("WindowSize must be positive, got: " + windowSize);
		}

		if (windowSize.toMillis() < 1000) {
			throw new IllegalArgumentException("WindowSize must be at least 1 second, got: " + windowSize);
		}
	}

	public int getLimit() {
		return limit;
	}

	public Duration getWindowSize() {
		return windowSize;
	}
	
}
