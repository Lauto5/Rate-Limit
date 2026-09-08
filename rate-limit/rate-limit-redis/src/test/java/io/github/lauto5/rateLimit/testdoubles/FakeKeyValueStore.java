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
 * Fake/in-memory implementation of {@link RedisTransactionPort} for unit tests.
 *
 * <p>Simulates WATCH/MULTI/EXEC atomicity: the {@link TransactionBody} is executed inside
 * a per-key atomic {@code compute}, so two threads never observe the same stale state
 * and lose updates, just as with the real protocol.
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
	 * Advances the virtual time, useful for expiration tests.
	 */
	public void advanceTime(long millis) {
		currentTime.addAndGet(millis);
		cleanup();
	}

	/**
	 * Clears all storage entries.
	 */
	public void clear() {
		store.clear();
		expirations.clear();
	}

	/**
	 * Checks whether a key exists and has not expired.
	 */
	public boolean exists(String identifier) {
		cleanup();
		return store.containsKey(identifier);
	}

	/**
	 * Gets the remaining TTL in milliseconds for a key.
	 * Returns null if the key does not exist or has already expired.
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
	 * Gets the value without checking expiration (for debugging).
	 */
	public byte[] getRaw(String identifier) {
		return store.get(identifier);
	}

	/**
	 * Writes a value directly, useful for pre-loading data (e.g. corrupted
	 * states) without going through the transactional semantics.
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