package io.github.lauto5.rateLimit.application.ports.in;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class RateLimitResult {

	final boolean allowed;
	
	final long remaining;
	
	final Optional<Duration> retryAfter;
	
	final Instant resetAt;
	
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
	
}
