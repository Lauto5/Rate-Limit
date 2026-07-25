package io.github.lauto5.rateLimit.domain.states;

import java.util.List;

public class SlidingWindowLogState implements AlgorithmState {

	private final List<Long> timestamps;

	public SlidingWindowLogState(List<Long> timestamps) {
		super();
		this.timestamps = timestamps;
	}

	public List<Long> getTimestamps() {
		return timestamps;
	}
	
}
