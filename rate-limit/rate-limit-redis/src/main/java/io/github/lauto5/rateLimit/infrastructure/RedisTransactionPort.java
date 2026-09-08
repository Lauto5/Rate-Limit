package io.github.lauto5.rateLimit.infrastructure;

/**
 * Low-level port for a Redis key-value transaction bounded to a single key.
 *
 * <p>{@link RedisTransactionPort#executeTransaction} executes the following sequence atomically
 * on a single connection:
 *
 * <pre>
 * WATCH key
 * GET key
 * ({@link TransactionBody} computes the new state and TTL)
 * MULTI; SET key value PX ttl; EXEC
 * </pre>
 *
 * If {@code EXEC} was aborted (another process modified the watched key), the method returns
 * {@code null} and the caller must retry with the most recent state.
 *
 * <p>This port extends {@link AutoCloseable} to release underlying resources.
 */
public interface RedisTransactionPort extends AutoCloseable {

	/**
	 * Runs {@code body} atomically against the given key.
	 *
	 * @param key  the key to watch and update
	 * @param body the body that turns the current value (or {@code null}) into the new value
	 *             and its TTL
	 * @param <T>  the type of the value produced by {@code body}
	 * @return the value produced by {@code body} if the transaction was committed, or
	 *         {@code null} if the transaction was aborted by a concurrent modification of the
	 *         watched key
	 */
	<T> T executeTransaction(String key, TransactionBody<T> body);

	/**
	 * Reads the current value of the given key without starting a transaction.
	 *
	 * @param key the key to read
	 * @return the stored bytes, or {@code null} if no such key exists
	 */
	byte[] get(String key);

}