package io.github.lauto5.rateLimit.domain.context;

import java.time.Instant;

/**
 * Provides the current instant to a rate-limiting algorithm during execution.
 *
 * <p>Holds a single {@code Instant} that represents the current time, enabling deterministic
 * testing and decoupling from the system clock.
 */
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
