package io.github.lauto5.rateLimit.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Immutable result of a rate-limit evaluation.
 *
 * <p>A {@code RateLimitResult} reports whether a request was {@link #isAllowed() allowed}, how
 * many requests remain in the current window ({@link #getRemaining}), when the limit resets
 * ({@link #getResetAt}) and, when denied, how long to wait before retrying
 * ({@link #getRetryAfter}).
 *
 * <p>Instances are typically created via the {@link #allowed} and {@link #denied} static
 * factories rather than the public constructor.
 */
public final class RateLimitResult {

	private final boolean allowed;
	
	private final long remaining;
	
	private final Optional<Duration> retryAfter;
	
	private final Instant resetAt;
	
	/**
	 * Creates a rate-limit result.
	 *
	 * @param allowed    whether the request was permitted
	 * @param remaining  the number of requests remaining before the limit is reached
	 * @param retryAfter the suggested delay before retrying, or {@code null} when allowed
	 * @param resetAt    the instant at which the current limit window resets
	 */
	public RateLimitResult(boolean allowed, long remaining, Duration retryAfter, Instant resetAt) {
		this.allowed = allowed;
		this.remaining = remaining;
		this.retryAfter = Optional.ofNullable(retryAfter);
		this.resetAt = resetAt;
	}
	
	/**
	 * Creates an <em>allowed</em> result.
	 *
	 * @param remaining the number of requests remaining before the limit is reached
	 * @param resetAt   the instant at which the current limit window resets
	 * @return an allowed {@code RateLimitResult}
	 */
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
	
	/**
	 * Creates a <em>denied</em> result.
	 *
	 * @param retryAfter the suggested delay before retrying
	 * @param resetAt    the instant at which the current limit window resets
	 * @return a denied {@code RateLimitResult}
	 */
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

	/**
	 * @return {@code true} if the request was permitted, {@code false} otherwise
	 */
	public boolean isAllowed() {
		return allowed;
	}

	/**
	 * @return the number of requests remaining before the limit is reached
	 */
	public long getRemaining() {
		return remaining;
	}

	/**
	 * @return an {@link Optional} containing the suggested retry delay when the request was
	 *         denied, or empty when it was allowed
	 */
	public Optional<Duration> getRetryAfter() {
		return retryAfter;
	}

	/**
	 * @return the instant at which the current limit window resets
	 */
	public Instant getResetAt() {
		return resetAt;
	}
	
}
