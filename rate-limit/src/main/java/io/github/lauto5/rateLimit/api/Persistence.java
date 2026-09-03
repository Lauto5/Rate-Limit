package io.github.lauto5.rateLimit.api;

import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.infraestructure.InMemoryStore;
import io.github.lauto5.rateLimit.infraestructure.LettuceKeyValueStore;
import io.github.lauto5.rateLimit.infraestructure.RedisStore;

/**
 * Static factory for the built-in {@link RateLimitStore} implementations.
 *
 * <p>Each factory method returns a concrete store that can be passed to
 * {@link io.github.lauto5.rateLimit.RateLimit#build} along with an algorithm to create a
 * limiter. Stores may be ephemeral (in-memory) or backed by an external system such as Redis.
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
		return new InMemoryStore();
	}
	
	/**
	 * Returns a new Redis-backed store connected to the given URL.
	 *
	 * <p>The returned store uses a compare-and-swap protocol with atomic retries to provide
	 * consistency across processes sharing the same Redis instance. It should be closed (via
	 * {@link AutoCloseable#close}) when no longer needed.
	 *
	 * @param url the Redis connection URL (for example {@code redis://localhost:6379})
	 * @return a {@link RedisStore} backed by {@link LettuceKeyValueStore}
	 */
	public static RateLimitStore inRedis(String url) {
		return new RedisStore(new LettuceKeyValueStore(url));
	}
	
}
