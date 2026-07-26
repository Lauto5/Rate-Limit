package io.github.lauto5.rateLimit.domain.model;

import java.time.Duration;

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
