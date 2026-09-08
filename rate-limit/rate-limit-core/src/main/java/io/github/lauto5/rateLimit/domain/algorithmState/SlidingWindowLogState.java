package io.github.lauto5.rateLimit.domain.algorithmState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serializable state for the sliding window log algorithm.
 *
 * <p>Holds the timestamps of the requests, in epoch milliseconds, that fall within the current
 * sliding window.
 */
public final class SlidingWindowLogState implements AlgorithmState {

	private final List<Long> timestamps;

	public SlidingWindowLogState(List<Long> timestamps) {
		this.timestamps = new ArrayList<>(timestamps);
	}

	public List<Long> getTimestamps() {
		return Collections.unmodifiableList(timestamps);
	}

}
