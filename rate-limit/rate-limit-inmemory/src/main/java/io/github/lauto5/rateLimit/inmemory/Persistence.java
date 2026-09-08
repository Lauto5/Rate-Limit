package io.github.lauto5.rateLimit.inmemory;

import io.github.lauto5.rateLimit.application.ports.out.Logger;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.infrastructure.InMemoryStore;
import io.github.lauto5.rateLimit.application.logging.NoOpLogger;

/**
 * Static factory for the in-memory {@link RateLimitStore} implementation.
 *
 * <p>This class belongs to the {@code rate-limit-inmemory} module. This dependency (in addition
 * to {@code rate-limit-core}) is required to use in-memory storage.
 *
 * <p>This class is not intended to be instantiated.
 */
public class Persistence {

	/**
	 * Returns a new in-memory store.
	 *
	 * <p>The returned store is thread-safe and suitable for single-process deployments, but
	 * state is lost when the process terminates.
	 *
	 * @return an {@link InMemoryStore} backed by a concurrent in-memory map
	 */
	public static RateLimitStore inMemory() {
		return inMemory(NoOpLogger.getInstance());
	}

	/**
	 * Returns a new in-memory store wired with the given logger.
	 *
	 * <p>The returned store is thread-safe and suitable for single-process deployments, but
	 * state is lost when the process terminates.
	 *
	 * @param logger the logger used to emit diagnostic output for store operations
	 * @return an {@link InMemoryStore} backed by a concurrent in-memory map
	 */
	public static RateLimitStore inMemory(Logger logger) {
		return new InMemoryStore(logger);
	}
}