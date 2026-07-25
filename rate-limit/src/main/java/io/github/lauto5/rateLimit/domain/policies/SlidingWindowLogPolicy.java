package io.github.lauto5.rateLimit.domain.policies;

import java.time.Duration;

public class SlidingWindowLogPolicy implements RateLimitPolicy {

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
