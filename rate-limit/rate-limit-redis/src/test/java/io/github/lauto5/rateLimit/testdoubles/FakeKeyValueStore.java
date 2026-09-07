package io.github.lauto5.rateLimit.testdoubles;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.lauto5.rateLimit.infrastructure.RedisTransactionPort;
import io.github.lauto5.rateLimit.infrastructure.TransactionBody;
import io.github.lauto5.rateLimit.infrastructure.TransactionWrite;

/**
 * Implementación fake/in-memory de {@link RedisTransactionPort} para pruebas unitarias.
 *
 * <p>Simula la atomicidad de WATCH/MULTI/EXEC: el {@link TransactionBody} se ejecuta dentro de
 * un {@code compute} atomico por clave, de modo que dos hilos nunca observan el mismo estado
 * obsoleto y pierden actualizaciones, igual que con el protocolo real.
 */
public final class FakeKeyValueStore implements RedisTransactionPort {

	private final Map<String, byte[]> store = new ConcurrentHashMap<>();
	private final Map<String, Long> expirations = new ConcurrentHashMap<>();
	private final AtomicLong currentTime = new AtomicLong(System.currentTimeMillis());

	@Override
	public <T> T executeTransaction(String key, TransactionBody<T> body) {
		cleanup();

		AtomicReference<T> produced = new AtomicReference<>();

		store.compute(key, (identifier, current) -> {

			TransactionWrite<T> write = body.apply(current);

			if (write == null) {
				return current;
			}

			produced.set(write.getResult());
			expirations.put(identifier, currentTime.get() + write.getTtlMillis());
			return write.getNewValue();
		});

		return produced.get();
	}

	/**
	 * Avanza el tiempo virtual, útil para pruebas de expiración.
	 */
	public void advanceTime(long millis) {
		currentTime.addAndGet(millis);
		cleanup();
	}

	/**
	 * Limpia todas las entradas del almacenamiento.
	 */
	public void clear() {
		store.clear();
		expirations.clear();
	}

	/**
	 * Verifica si una clave existe y no ha expirado.
	 */
	public boolean exists(String identifier) {
		cleanup();
		return store.containsKey(identifier);
	}

	/**
	 * Obtiene el TTL restante en milisegundos para una clave.
	 * Retorna null si la clave no existe o ya expiró.
	 */
	public Long getRemainingTtl(String identifier) {
		cleanup();
		Long expiry = expirations.get(identifier);
		if (expiry == null) {
			return null;
		}
		long remaining = expiry - currentTime.get();
		return remaining > 0 ? remaining : null;
	}

	/**
	 * Obtiene el valor sin verificar expiración (para debugging).
	 */
	public byte[] getRaw(String identifier) {
		return store.get(identifier);
	}

	/**
	 * Escribe directamente un valor, útil para pre-cargar datos (por ejemplo, estados
	 * corruptos) sin pasar por la semántica transaccional.
	 */
	public void putRaw(String identifier, byte[] value) {
		expirations.remove(identifier);
		store.put(identifier, Arrays.copyOf(value, value.length));
	}

	private void cleanup() {
		long now = currentTime.get();
		expirations.entrySet().removeIf(entry -> {
			if (now > entry.getValue()) {
				store.remove(entry.getKey());
				return true;
			}
			return false;
		});
	}

	@Override
	public byte[] get(String identifier) {
		cleanup();
		return store.get(identifier);
	}

	@Override
	public void close() {
		clear();
	}
}