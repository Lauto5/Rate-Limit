package io.github.lauto5.rateLimit.domain.states;

import java.util.Map;

public class SlidingWindowCounterState implements AlgorithmState {

	private final Map<Long, Integer> windows;

	public SlidingWindowCounterState(Map<Long, Integer> windows) {
		super();
		this.windows = windows;
	}

	public Map<Long, Integer> getWindows() {
		return windows;
	}
	
}
