package io.github.lauto5.rateLimit.domain.model;

import java.time.Duration;

/**
 * A decision indicating that a request was denied.
 *
 * <p>Holds the duration after which a new request may be retried.
 */
public final class DeniedDecision implements AlgorithmDecision {

    private final Duration retryAfter;

    public DeniedDecision(Duration retryAfter) {
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

	@Override
	public boolean isAllowed() {
		return false;
	}
	
}
