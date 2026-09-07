package io.github.lauto5.rateLimit.infrastructure;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.infrastructure.KeyValueStorePort;
import io.github.lauto5.rateLimit.application.ports.out.Logger;
import io.github.lauto5.rateLimit.logging.NoOpLogger;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public class RedisStore implements RateLimitStore , AutoCloseable{

	private static final int MAX_RETRIES = 10;

	private static final long MIN_TTL_MILLIS = 1L;

	private final KeyValueStorePort keyValueStore;
	private final Logger logger;

	public RedisStore(KeyValueStorePort keyValueStore) {
		this(keyValueStore, NoOpLogger.getInstance());
	}

	public RedisStore(KeyValueStorePort keyValueStore, Logger logger) {
		super();
		this.keyValueStore = keyValueStore;
		this.logger = logger;
	}

	@Override
	public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(String identifier,
			AtomicOperation<S> operation) {

		StateCodec<S> codec = operation.getCodec();

		/*
		 * 1 :
		 *
		 * Reintentamos hasta MAX_RETRIES veces si el CAS falla
		 * por una escritura concurrente de otro proceso/hilo.
		 */

		for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {

			byte[] currentBytes = keyValueStore.get(identifier);

			StoreState<S> currentStoreState = toStoreState(currentBytes, codec);

			AtomicOperationResult<S> result = operation.apply(currentStoreState);

			byte[] newBytes = codec.encode(result.getState());

			long ttlMillis = calculateTtlMillis(operation.getNow(), result.getExpiresAt());

			boolean applied = keyValueStore.compareAndSwap(identifier, currentBytes, newBytes, ttlMillis);

			if (applied) {
				logger.debug("Atomic update applied for identifier '" + identifier
						+ "' with TTL " + ttlMillis + "ms");
				return result;
			}

			logger.debug("Compare-and-swap collision for identifier '" + identifier
					+ "' on attempt " + (attempt + 1) + "; retrying");

		}

		throw new IllegalStateException(
				"No se pudo aplicar la operacion atomica sobre '" + identifier
						+ "' tras " + MAX_RETRIES + " intentos (alta contencion)"
		);

	}

	private <S extends AlgorithmState> StoreState<S> toStoreState(byte[] currentBytes, StateCodec<S> codec) {

		if (currentBytes == null) {
			return null;
		}

		S decodedState = codec.decode(currentBytes);

		return new StoreState<>(decodedState, Instant.EPOCH);
	}

	private long calculateTtlMillis(Instant now, Instant expiresAt) {

		long ttlMillis = Duration.between(now, expiresAt).toMillis();

		return Math.max(ttlMillis, MIN_TTL_MILLIS);
	}



	@Override
	public void close() throws Exception {
		this.keyValueStore.close();
	}

}
