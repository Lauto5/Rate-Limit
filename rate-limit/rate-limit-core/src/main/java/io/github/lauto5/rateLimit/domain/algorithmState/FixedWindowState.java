package io.github.lauto5.rateLimit.domain.algorithmState;

import java.time.Instant;

/**
 * Serializable state for the fixed window algorithm.
 *
 * <p>Holds the number of requests counted within the current window and the instant at which
 * the window started.
 */
public class FixedWindowState implements AlgorithmState {

	private final int count;
	
	private final Instant windowStart;

	public FixedWindowState(int count, Instant windowStart) {
		
		super();
		
        if (count < 0) {
            throw new IllegalArgumentException(
                "Count cannot be negative, got: " + count
            );
        }
        
        if (windowStart == null) {
            throw new IllegalArgumentException("WindowStart cannot be null");
        }
		
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
