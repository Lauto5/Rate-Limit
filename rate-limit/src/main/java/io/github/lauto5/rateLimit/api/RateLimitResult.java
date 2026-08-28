package io.github.lauto5.rateLimit.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class RateLimitResult {

	private final boolean allowed;
	
	private final long remaining;
	
	private final Optional<Duration> retryAfter;
	
	private final Instant resetAt;
	
	public RateLimitResult(boolean allowed, long remaining, Duration retryAfter, Instant resetAt) {
		this.allowed = allowed;
		this.remaining = remaining;
		this.retryAfter = Optional.ofNullable(retryAfter);
		this.resetAt = resetAt;
	}
	
	public static RateLimitResult allowed(
	        long remaining,
	        Instant resetAt) {

	    return new RateLimitResult(
	            true,
	            remaining,
	            null,
	            resetAt
	    );
	}
	
	public static RateLimitResult denied(
	        Duration retryAfter,
	        Instant resetAt) {

	    return new RateLimitResult(
	            false,
	            0,
	            retryAfter,
	            resetAt
	    );
	}

	public boolean isAllowed() {
		return allowed;
	}

	public long getRemaining() {
		return remaining;
	}

	public Optional<Duration> getRetryAfter() {
		return retryAfter;
	}

	public Instant getResetAt() {
		return resetAt;
	}
	
}
