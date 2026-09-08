package io.github.lauto5.rateLimit.domain.algorithmState;

import java.util.Map;

/**
 * Serializable state for the sliding window counter algorithm.
 *
 * <p>Holds the request count for each relevant sub-window, keyed by its window bucket index.
 */
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
