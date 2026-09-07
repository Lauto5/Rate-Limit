package io.github.lauto5.rateLimit.redis;

import io.github.lauto5.rateLimit.application.ports.out.Logger;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.infrastructure.LettuceTransactionPort;
import io.github.lauto5.rateLimit.infrastructure.RedisStore;
import io.github.lauto5.rateLimit.logging.NoOpLogger;

/**
 * Static factory for the Redis-backed {@link RateLimitStore} implementation.
 *
 * <p>Esta clase pertenece al modulo {@code rate-limit-redis}. Se agregan esta y la dependencia
 * {@code rate-limit-core} para usar un servidor Redis existente.
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
	 * @param url the Redis connection URL (for example {@code redis://localhost:6379})
	 * @return a {@link RedisStore} backed by {@link LettuceTransactionPort}
	 */
	public static RateLimitStore inRedis(String url) {
		return inRedis(url, NoOpLogger.getInstance());
	}

	/**
	 * Returns a new Redis-backed store connected to the given URL, wired with the given logger.
	 *
	 * <p>The returned store uses a {@code WATCH / MULTI / EXEC} protocol with atomic retries to
	 * provide consistency across processes sharing the same Redis instance. It should be closed
	 * (via {@link AutoCloseable#close}) when no longer needed.
	 *
	 * @param url    the Redis connection URL (for example {@code redis://localhost:6379})
	 * @param logger the logger used to emit diagnostic output for store operations
	 * @return a {@link RedisStore} backed by {@link LettuceTransactionPort}
	 */
	public static RateLimitStore inRedis(String url, Logger logger) {
		return new RedisStore(new LettuceTransactionPort(url), logger);
	}
}