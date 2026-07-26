package io.github.lauto5.rateLimit.domain.algorithmState;

import java.time.Instant;

public class FixedWindowState implements AlgorithmState {

	private final int count;
	
	private final Instant windowStart;

	public FixedWindowState(int count, Instant windowStart) {
		super();
		this.count = count;
		this.windowStart = windowStart;
	}

	public int getCount() {
		return count;
	}

	public Instant getWindowStart() {
		return windowStart;
	}
	
}
