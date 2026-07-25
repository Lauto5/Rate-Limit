package io.github.lauto5.rateLimit.application.ports.in;

import java.time.Duration;
import java.time.Instant;

public class RateLimitResult {

	final boolean allowed;
	
	final long remaining;
	
	final Duration retryAfter;
	
	final Instant resetAt;

	public RateLimitResult(boolean allowed, long remaining, Duration retryAfter, Instant resetAt) {
		this.allowed = allowed;
		this.remaining = remaining;
		this.retryAfter = retryAfter;
		this.resetAt = resetAt;
	}
	
}
