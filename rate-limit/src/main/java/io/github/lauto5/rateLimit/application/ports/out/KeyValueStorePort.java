package io.github.lauto5.rateLimit.application.ports.out;

/**
 * Low-level port abstraction for a key-value store supporting atomic compare-and-swap.
 *
 * <p>Implementations provide raw {@code byte[]} storage keyed by an identifier, together with a
 * compare-and-swap operation that atomically updates a value only when its current value
 * matches an expected one. This enables concurrency-safe persistence of rate-limit state.
 *
 * <p>This port extends {@link AutoCloseable} so underlying resources can be released.
 */
public interface KeyValueStorePort extends AutoCloseable {

	/**
	 * Retrieves the value currently associated with the given identifier.
	 *
	 * @param identifier the key to look up
	 * @return the stored bytes, or {@code null} if no such key exists
	 */
	byte[] get(String identifier);

	/**
	 * Atomically replaces the value of {@code identifier} with {@code newValue} only if the
	 * current value exactly matches {@code expectedValue}.
	 *
	 * <p>If {@code expectedValue} is {@code null}, the operation expects the key to not exist
	 * yet (initial write). A time-to-live (TTL) may be attached to the written value.
	 *
	 * @param identifier   the key to update
	 * @param expectedValue the value the key must currently hold, or {@code null} for an
	 *                      initial write
	 * @param newValue      the value to write
	 * @param ttlMillis     the time-to-live in milliseconds for the written value
	 * @return {@code true} if the replacement was applied, {@code false} if there was a
	 *         conflict (the current value did not match the expected one)
	 */
	boolean compareAndSwap(String identifier, byte[] expectedValue, byte[] newValue, long ttlMillis);

}
