package io.github.lauto5.rateLimit.domain.context;

import java.time.Instant;

public class AlgorithmContext {

	private final Instant now;

	public AlgorithmContext(Instant now) {
		super();
		
        if (now == null) {
            throw new IllegalArgumentException("Now cannot be null");
        }
		
		this.now = now;
	}

	public Instant getNow() {
		return now;
	}
	
}
