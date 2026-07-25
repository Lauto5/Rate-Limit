package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Instant;

public class AlgorithmContext {

	private final Instant now;

	public AlgorithmContext(Instant now) {
		super();
		this.now = now;
	}

	public Instant getNow() {
		return now;
	}
	
}
