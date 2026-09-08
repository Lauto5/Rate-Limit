package io.github.lauto5.rateLimit.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import io.github.lauto5.rateLimit.application.CorruptedStateException;
import io.github.lauto5.rateLimit.application.VersionedStateCodec;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.Logger;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.algorithm.StateCodec;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.application.logging.NoOpLogger;

/**
 * {@link RateLimitStore} backed by a Redis key holding the serialized algorithm state.
 *
 * <p>Atomicity is achieved with the {@link RedisTransactionPort#executeTransaction} protocol:
 * the key is watched, its current state is read, the {@link AtomicOperation} computes the new
 * state in Java, and {@link RedisTransactionPort#executeTransaction} persists it with
 * {@code MULTI/SET/EXEC}. If another process modified the watched key, {@code EXEC} aborts and
 * the cycle is retried with the most recent state up to {@link #MAX_RETRIES} attempts.
 *
 * <p><strong>Worst-case latency:</strong> an exponential back-off with jitter (0..64&thinsp;ms cap)
 * is applied between retries. With {@code MAX_RETRIES = 25}, continuous contention on the same
 * key adds at most approximately 1.3&nbsp;s of additional delay before an
 * {@link IllegalStateException} is thrown.
 *
 * <p>State is serialized with {@link VersionedStateCodec}; corrupted or
 * incompatible-version data is treated as absent (with a warning) rather than causing a failure.
 *
 * <p>Redis keys are stored under a <strong>namespace</strong> ({@link #DEFAULT_NAMESPACE} by
 * default, configurable via constructor) to prevent collisions between applications,
 * environments, versions, or rate limiters that share the same Redis instance.
 */
public class RedisStore implements RateLimitStore, AutoCloseable {

	private static final int MAX_RETRIES = 25;

	private static final long MIN_TTL_MILLIS = 1L;

	private static final int MAX_IDENTIFIER_LENGTH = 512;

	/**
	 * Default namespace for Redis keys. Keys are constructed as
	 * {@code namespace + ":" + identifier}.
	 */
	public static final String DEFAULT_NAMESPACE = "rate-limit";

	private final RedisTransactionPort keyValueStore;
	private final Logger logger;
	private final String namespace;

	public RedisStore(RedisTransactionPort keyValueStore) {
		this(keyValueStore, NoOpLogger.getInstance(), DEFAULT_NAMESPACE);
	}

	public RedisStore(RedisTransactionPort keyValueStore, Logger logger) {
		this(keyValueStore, logger, DEFAULT_NAMESPACE);
	}

	public RedisStore(RedisTransactionPort keyValueStore, String namespace) {
		this(keyValueStore, NoOpLogger.getInstance(), namespace);
	}

	public RedisStore(RedisTransactionPort keyValueStore, Logger logger, String namespace) {
		super();
		this.keyValueStore = keyValueStore;
		this.logger = logger;

		if (namespace == null || namespace.trim().isEmpty()) {
			throw new IllegalArgumentException("Namespace must not be null or empty");
		}
		this.namespace = namespace;
	}

	@Override
	public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(String identifier,
			AtomicOperation<S> operation) {

		String key = buildKey(identifier);

		StateCodec<S> wireCodec = new VersionedStateCodec<>(operation.getCodec());

		/*
		 * The number of retries is bounded by MAX_RETRIES. This value is validated by the
		 * adapter's concurrency tests; if contention is high enough to exhaust all retries, the
		 * operation is aborted rather than degrading indefinitely.
		 */

		TransactionBody<AtomicOperationResult<S>> body = currentBytes -> {

			StoreState<S> currentStoreState = toStoreState(currentBytes, wireCodec, identifier);

			AtomicOperationResult<S> result = operation.apply(currentStoreState);

			byte[] newBytes = wireCodec.encode(result.getState());

			long ttlMillis = calculateTtlMillis(operation.getNow(), result.getExpiresAt());

			return new TransactionWrite<>(newBytes, ttlMillis, result);
		};

		for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {

			AtomicOperationResult<S> result = keyValueStore.executeTransaction(key, body);

			if (result != null) {
				long ttlMillis = calculateTtlMillis(operation.getNow(), result.getExpiresAt());
				logger.debug("Atomic update applied for identifier '" + identifier
						+ "' with TTL " + ttlMillis + "ms");
				return result;
			}

			logger.debug("WATCH/MULTI/EXEC conflict for identifier '" + identifier
					+ "' on attempt " + (attempt + 1) + "; retrying");

		/*
		 * Exponential back-off with full jitter (0..2^attempt, cap 64 ms) to desynchronize
		 * retries under high contention: without the wait, all contenders look again at the
		 * same time and may starve each other (livelock).
		 */
			if (attempt < MAX_RETRIES - 1) {
				backoffBeforeRetry(attempt);
			}

		}

		throw new IllegalStateException(
				"Could not apply atomic operation on '" + identifier
						+ "' after " + MAX_RETRIES + " attempts (high contention)"
		);

	}

	private <S extends AlgorithmState> StoreState<S> toStoreState(byte[] currentBytes, StateCodec<S> codec,
			String identifier) {

		if (currentBytes == null) {
			return null;
		}

		try {
			S decodedState = codec.decode(currentBytes);

		/*
		 * Invariant: StoreState.expiresAt is persistence metadata exclusively; the algorithm
		 * never reads it (its state carries its own temporal fields). In Redis, expiry is
		 * enforced by the key's TTL: if the key exists, the state is not expired. Therefore
		 * the Instant.EPOCH placeholder is valid here, and expiry is evaluated by key
		 * presence rather than by clock.
		 */
			return new StoreState<>(decodedState, Instant.EPOCH);
		} catch (CorruptedStateException e) {
			logger.warn("Corrupted or incompatible-format state for '" + identifier
					+ "'; rewriting from scratch: " + e.getMessage());
			return null;
		}

	}

	private String buildKey(String identifier) {

		if (identifier == null || identifier.isEmpty()) {
			throw new IllegalArgumentException("Identifier must not be null or empty");
		}
		if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
			throw new IllegalArgumentException("Identifier exceeds the maximum allowed length ("
					+ MAX_IDENTIFIER_LENGTH + " characters)");
		}

		return this.namespace + ":" + identifier;
	}

	private long calculateTtlMillis(Instant now, Instant expiresAt) {

		long ttlMillis = Duration.between(now, expiresAt).toMillis();

		return Math.max(ttlMillis, MIN_TTL_MILLIS);
	}

	private void backoffBeforeRetry(int attempt) {

		long maxMillis = Math.min(1L << attempt, 64L);
		long sleepMillis = ThreadLocalRandom.current().nextLong(0L, maxMillis + 1L);

		if (sleepMillis == 0L) {
			return;
		}

		try {
			Thread.sleep(sleepMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Retry interrupted", e);
		}
	}

	@Override
	public void close() throws Exception {
		this.keyValueStore.close();
	}

}