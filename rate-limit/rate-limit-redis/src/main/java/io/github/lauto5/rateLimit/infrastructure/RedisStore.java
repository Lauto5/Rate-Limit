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
import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.logging.NoOpLogger;

/**
 * {@link RateLimitStore} backed by a Redis key holding the serialized algorithm state.
 *
 * <p>La atomicidad se consigue con el protocolo <code>WATCH / MULTI / EXEC</code>: se observa
 * la key, se lee su estado, la {@link AtomicOperation} calcula el nuevo estado en Java, y el
 * {@link RedisTransactionPort#executeTransaction} escribe con {@code MULTI/SET/EXEC}. Si otro
 * proceso modifico la key observada, {@code EXEC} aborta y el ciclo se reintenta con el estado
 * mas reciente hasta {@link #MAX_RETRIES} intentos.
 *
 * <p><strong>Latencia en el peor caso:</strong> entre reintentos se espera un backoff
 * exponencial con jitter (0..64 ms tope). Con {@code MAX_RETRIES = 25}, una disputa continua
 * por la misma key agrega a lo sumo ~1,3 s adicionales antes de lanzar
 * {@link IllegalStateException}.
 *
 * <p>El estado se serializa con {@link VersionedStateCodec}; los datos corruptos o de una
 * version incompatible se tratan como estado inexistente (con warning) en lugar de fallar.
 *
 * <p>Las keys Redis se escriben bajo un <strong>namespace</strong> ({@link
 * #DEFAULT_NAMESPACE} por defecto, configurable por constructor) para evitar colisiones entre
 * aplicaciones, ambientes, versiones o limitadores que compartan el mismo Redis.
 */
public class RedisStore implements RateLimitStore, AutoCloseable {

	private static final int MAX_RETRIES = 25;

	private static final long MIN_TTL_MILLIS = 1L;

	private static final int MAX_IDENTIFIER_LENGTH = 512;

	/**
	 * Namespace por defecto para las keys Redis. Las keys se construyen como
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
			throw new IllegalArgumentException("El namespace no puede ser null o vacio");
		}
		this.namespace = namespace;
	}

	@Override
	public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(String identifier,
			AtomicOperation<S> operation) {

		String key = buildKey(identifier);

		StateCodec<S> wireCodec = new VersionedStateCodec<>(operation.getCodec());

		/*
		 * La cantidad de reintentos queda acotada por MAX_RETRIES. El valor se valida con los
		 * tests de concurrencia del adapter; si la contencion es tan alta que se agota, se
		 * aborta la operacion en lugar de degradar indefinidamente.
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
			 * Backoff exponencial con jitter completo (0..2^intento, tope 64ms) para
			 * desincronizar los reintentos bajo contencion alta: sin la espera, todos los
			 * contendientes vuelven a mirar a la vez y pueden starvearse mutuamente (livelock).
			 */
			if (attempt < MAX_RETRIES - 1) {
				backoffBeforeRetry(attempt);
			}

		}

		throw new IllegalStateException(
				"No se pudo aplicar la operacion atomica sobre '" + identifier
						+ "' tras " + MAX_RETRIES + " intentos (alta contencion)"
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
			 * Invariante: StoreState.expiresAt es metadata de persistencia exclusivamente;
			 * el algoritmo nunca la lee (su state porta sus propios campos temporales). En
			 * Redis la expiracion la aplica el TTL de la key: si la key existe, el estado no
			 * esta expirado. Por eso el placeholder Instant.EPOCH es valido aqui y la
			 * expiracion no se evalua por reloj, sino por presencia de la key.
			 */
			return new StoreState<>(decodedState, Instant.EPOCH);
		} catch (CorruptedStateException e) {
			logger.warn("Estado corrupto o de formato incompatible para '" + identifier
					+ "'; se reescribe desde cero: " + e.getMessage());
			return null;
		}

	}

	private String buildKey(String identifier) {

		if (identifier == null || identifier.isEmpty()) {
			throw new IllegalArgumentException("El identifier no puede ser null o vacio");
		}
		if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
			throw new IllegalArgumentException("El identifier supera la longitud maxima permitida ("
					+ MAX_IDENTIFIER_LENGTH + " caracteres)");
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
			throw new IllegalStateException("Reintento interrumpido", e);
		}
	}

	@Override
	public void close() throws Exception {
		this.keyValueStore.close();
	}

}