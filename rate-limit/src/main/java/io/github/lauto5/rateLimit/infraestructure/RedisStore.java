package io.github.lauto5.rateLimit.infraestructure;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public class RedisStore implements RateLimitStore, AutoCloseable {

	private static final RedisCodec<String, byte[]> WIRE_CODEC = RedisCodec.of(
			StringCodec.UTF8,
			ByteArrayCodec.INSTANCE
	);

	private static final String CAS_SCRIPT =
			"local current = redis.call('GET', KEYS[1]) "
			+ "local expectedExists = ARGV[1] "
			+ "if expectedExists == '1' then "
			+ "  if current == false or current ~= ARGV[2] then return 0 end "
			+ "else "
			+ "  if current ~= false then return 0 end "
			+ "end "
			+ "redis.call('SET', KEYS[1], ARGV[3], 'PX', ARGV[4]) "
			+ "return 1";

	private static final int MAX_RETRIES = 10;

	private static final long MIN_TTL_MILLIS = 1L;

	private final StatefulRedisConnection<String, byte[]> connection;

	public RedisStore(RedisClient client) {
		super();
		this.connection = client.connect(WIRE_CODEC);
	}

	@Override
	public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(String identifier,
			AtomicOperation<S> operation) {

		StateCodec<S> codec = operation.getCodec();

		RedisCommands<String, byte[]> commands = connection.sync();

		/*
		 * 1 :
		 *
		 * Reintentamos hasta MAX_RETRIES veces si el CAS falla
		 * por una escritura concurrente de otro proceso/hilo.
		 */

		for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {

			byte[] currentBytes = commands.get(identifier);

			StoreState<S> currentStoreState = toStoreState(currentBytes, codec);

			AtomicOperationResult<S> result = operation.apply(currentStoreState);

			byte[] newBytes = codec.encode(result.getState());

			long ttlMillis = calculateTtlMillis(operation.getNow(), result.getExpiresAt());

			boolean applied = compareAndSwap(commands, identifier, currentBytes, newBytes, ttlMillis);

			if (applied) {
				return result;
			}

			/*
			 * Conflicto: otro proceso escribió sobre esta key
			 * entre nuestro GET y nuestro intento de SET.
			 * Reintentamos leyendo el valor mas reciente.
			 */

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

		/*
		 * NOTA: expiresAt no se usa por ningun algoritmo dentro de
		 * apply() (solo se lee state.getState()); Redis maneja el TTL
		 * de forma nativa via PX. Se pasa un valor dummy solo para
		 * satisfacer el constructor de StoreState.
		 */

		return new StoreState<>(decodedState, Instant.EPOCH);
	}

	private long calculateTtlMillis(Instant now, Instant expiresAt) {

		long ttlMillis = Duration.between(now, expiresAt).toMillis();

		return Math.max(ttlMillis, MIN_TTL_MILLIS);
	}

	private boolean compareAndSwap(RedisCommands<String, byte[]> commands, String identifier, byte[] currentBytes,
			byte[] newBytes, long ttlMillis) {

		String expectedExists = (currentBytes != null) ? "1" : "0";

		byte[] expectedValue = (currentBytes != null) ? currentBytes : new byte[0];

		Long casResult = commands.eval(
				CAS_SCRIPT,
				ScriptOutputType.INTEGER,
				new String[] { identifier },
				expectedExists.getBytes(StandardCharsets.UTF_8),
				expectedValue,
				newBytes,
				String.valueOf(ttlMillis).getBytes(StandardCharsets.UTF_8)
		);

		return Long.valueOf(1L).equals(casResult);
	}

	@Override
	public void close() {
		connection.close();
	}

}
