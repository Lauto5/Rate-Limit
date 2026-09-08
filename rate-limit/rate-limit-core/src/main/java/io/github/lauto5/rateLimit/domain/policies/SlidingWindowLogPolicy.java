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
		this.limit = limit;
		this.windowSize = windowSize;
	}

	public int getLimit() {
		return limit;
	}

	public Duration getWindowSize() {
		return windowSize;
	}
	
}
