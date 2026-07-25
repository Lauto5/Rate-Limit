package io.github.lauto5.rateLimit.domain.policies;

import java.time.Duration;

public class SlidingWindowCounterPolicy implements RateLimitPolicy {

	private final int limit;
	private final Duration windowSize;
	private final int subWindows;
	
	public SlidingWindowCounterPolicy(int limit, Duration windowSize, int subWindows) {
		super();
		this.limit = limit;
		this.windowSize = windowSize;
		this.subWindows = subWindows;
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
