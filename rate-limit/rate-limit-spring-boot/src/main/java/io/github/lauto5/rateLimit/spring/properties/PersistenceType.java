package io.github.lauto5.rateLimit.spring.properties;

/**
 * Supported outbound persistence adapters for the rate limit integration.
 */
public enum PersistenceType {

	/**
	 * In-memory {@code RateLimitStore} (single-process deployments).
	 */
	INMEMORY,

	/**
	 * Redis-backed {@code RateLimitStore}.
	 */
	REDIS
}