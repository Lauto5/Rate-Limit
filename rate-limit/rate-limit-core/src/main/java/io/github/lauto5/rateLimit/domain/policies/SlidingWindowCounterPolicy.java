package io.github.lauto5.rateLimit.domain.policies;

import java.time.Duration;

/**
 * Configuration for the sliding window counter algorithm.
 *
 * <p>Defines the maximum number of requests ({@code limit}) permitted within a sliding window
 * of the given {@code windowSize}, which is further divided into {@code subWindows} equal
 * segments for weighted counting.
 */
public final class SlidingWindowCounterPolicy implements RateLimitPolicy {

	private final int limit;
	private final Duration windowSize;
	private final int subWindows;
	
	public SlidingWindowCounterPolicy(int limit, Duration windowSize, int subWindows) {
		super();

		validateLimit(limit);
		validateWindowSize(windowSize);
		validateSubWindows(subWindows);

		this.limit = limit;
		this.windowSize = windowSize;
		this.subWindows = subWindows;
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

	private void validateSubWindows(int subWindows) {

		if (subWindows <= 0) {
			throw new IllegalArgumentException("SubWindows must be greater than 0, got: " + subWindows);
		}
	}

	public int getLimit() {
		return limit;
	}

	public Duration getWindowSize() {
		return windowSize;
	}

	public int getSubWindows() {
		return subWindows;
	}
	
}
