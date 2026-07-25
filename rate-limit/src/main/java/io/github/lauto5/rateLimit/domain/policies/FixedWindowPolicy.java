package io.github.lauto5.rateLimit.domain.policies;

import java.time.Duration;

public class FixedWindowPolicy implements RateLimitPolicy {

	private final int limit;
	
	private final Duration windowSize;

	public FixedWindowPolicy(int limit, Duration windowSize) {
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
