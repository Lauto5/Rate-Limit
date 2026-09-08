package io.github.lauto5.rateLimit.infrastructure;

/**
 * Functional body of a Redis transaction: turns the current value of a key (or {@code null} if
 * absent) into the value to write and its time-to-live, plus a result for the caller.
 *
 * <p>It is invoked by {@link RedisTransactionPort#executeTransaction} between the
 * {@code WATCH} and the {@code MULTI}/{@code EXEC}; if a conflict occurs the transaction is
 * aborted and the caller retries without re-executing this body with stale data.
 *
 * @param <T> the type of the result carried back to the caller
 */
@FunctionalInterface
public interface TransactionBody<T> {

	/**
	 * Computes the value to persist based on the current state of the key.
	 *
	 * @param currentValue the current stored bytes, or {@code null} if the key does not exist
	 * @return the write to perform, or {@code null} to skip the write and abort this iteration
	 */
	TransactionWrite<T> apply(byte[] currentValue);

}