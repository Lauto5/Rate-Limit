package io.github.lauto5.rateLimit.redis;

import io.github.lauto5.rateLimit.application.ports.out.Logger;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.infrastructure.LettuceTransactionPort;
import io.github.lauto5.rateLimit.infrastructure.RedisStore;
import io.github.lauto5.rateLimit.logging.NoOpLogger;

/**
 * Static factory for the Redis-backed {@link RateLimitStore} implementation.
 *
 * <p>This class belongs to the {@code rate-limit-redis} module. This dependency and
 * {@code rate-limit-core} are required to use an existing Redis server.
 *
 * <p>This class is not intended to be instantiated.
 */
public class Persistence {

	/**
	 * Returns a new Redis-backed store connected to the given URL.
	 *
	 * <p>The returned store uses a {@code WATCH / MULTI / EXEC} protocol with atomic retries to
	 * provide consistency across processes sharing the same Redis instance. It should be closed
	 * (via {@link AutoCloseable#close}) when no longer needed.
	 *
	 * <p>Keys are written under the default namespace {@link RedisStore#DEFAULT_NAMESPACE}.
	 *
	 * @param url the Redis connection URL (for example {@code redis://localhost:6379})
	 * @return a {@link RedisStore} backed by {@link LettuceTransactionPort}
	 */
	public static RateLimitStore inRedis(String url) {
		return inRedis(url, NoOpLogger.getInstance(), RedisStore.DEFAULT_NAMESPACE);
	}

	/**
	 * Returns a new Redis-backed store connected to the given URL, wired with the given logger.
	 *
	 * <p>The returned store uses a {@code WATCH / MULTI / EXEC} protocol with atomic retries to
	 * provide consistency across processes sharing the same Redis instance. It should be closed
	 * (via {@link AutoCloseable#close}) when no longer needed.
	 *
	 * <p>Keys are written under the default namespace {@link RedisStore#DEFAULT_NAMESPACE}.
	 *
	 * @param url    the Redis connection URL (for example {@code redis://localhost:6379})
	 * @param logger the logger used to emit diagnostic output for store operations
	 * @return a {@link RedisStore} backed by {@link LettuceTransactionPort}
	 */
	public static RateLimitStore inRedis(String url, Logger logger) {
		return inRedis(url, logger, RedisStore.DEFAULT_NAMESPACE);
	}

	/**
	 * Returns a new Redis-backed store that writes its keys under the given namespace.
	 *
	 * <p>The returned store uses a {@code WATCH / MULTI / EXEC} protocol with atomic retries to
	 * provide consistency across processes sharing the same Redis instance. It should be closed
	 * (via {@link AutoCloseable#close}) when no longer needed.
	 *
	 * <p>The namespace allows a single Redis instance to be shared across applications,
	 * environments, or versions without collisions: each key is written as
	 * {@code namespace + ":" + identifier}.
	 *
	 * @param url       the Redis connection URL (for example {@code redis://localhost:6379})
	 * @param namespace the namespace under which all rate-limit keys are stored; never null or empty
	 * @return a {@link RedisStore} backed by {@link LettuceTransactionPort}
	 */
	public static RateLimitStore inRedis(String url, String namespace) {
		return inRedis(url, NoOpLogger.getInstance(), namespace);
	}

	/**
	 * Returns a new Redis-backed store connected to the given URL, wired with the given logger,
	 * that writes its keys under the given namespace.
	 *
	 * <p>The returned store uses a {@code WATCH / MULTI / EXEC} protocol with atomic retries to
	 * provide consistency across processes sharing the same Redis instance. It should be closed
	 * (via {@link AutoCloseable#close}) when no longer needed.
	 *
	 * <p>The namespace allows a single Redis instance to be shared across applications,
	 * environments, or versions without collisions: each key is written as
	 * {@code namespace + ":" + identifier}.
	 *
	 * @param url       the Redis connection URL (for example {@code redis://localhost:6379})
	 * @param logger    the logger used to emit diagnostic output for store operations
	 * @param namespace the namespace under which all rate-limit keys are stored; never null or empty
	 * @return a {@link RedisStore} backed by {@link LettuceTransactionPort}
	 */
	public static RateLimitStore inRedis(String url, Logger logger, String namespace) {
		return new RedisStore(new LettuceTransactionPort(url), logger, namespace);
	}
}