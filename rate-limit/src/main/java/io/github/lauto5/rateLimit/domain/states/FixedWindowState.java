package io.github.lauto5.rateLimit.domain.states;

public class FixedWindowState implements AlgorithmState {

	private final int count;
	
	private final long windowStart;

	public FixedWindowState(int count, long windowStart) {
		super();
		this.count = count;
		this.windowStart = windowStart;
	}

	public int getCount() {
		return count;
	}

	public long getWindowStart() {
		return windowStart;
	}
	
}
