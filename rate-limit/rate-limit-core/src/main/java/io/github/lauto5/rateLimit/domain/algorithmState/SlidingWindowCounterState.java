package io.github.lauto5.rateLimit.domain.algorithmState;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializable state for the sliding window counter algorithm.
 *
 * <p>Holds the request count for each relevant sub-window, keyed by its window bucket index.
 */
public final class SlidingWindowCounterState implements AlgorithmState {

	private final Map<Long, Integer> windows;

	public SlidingWindowCounterState(Map<Long, Integer> windows) {
		this.windows = new HashMap<>(windows);
	}

	public Map<Long, Integer> getWindows() {
		return Collections.unmodifiableMap(windows);
	}

}
