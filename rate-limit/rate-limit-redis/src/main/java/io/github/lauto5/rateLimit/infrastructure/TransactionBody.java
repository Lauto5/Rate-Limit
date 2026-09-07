package io.github.lauto5.rateLimit.infrastructure;

/**
 * Functional body of a Redis transaction: turns the current value of a key (or {@code null} if
 * absent) into the value to write and its time-to-live, plus a result for the caller.
 *
 * <p>Es invocada por {@link RedisTransactionPort#executeTransaction} entre el {@code WATCH} y
 * el {@code MULTI}/{@code EXEC}; en caso de conflicto la transaccion aborta y el llamador
 * reintenta sin re-ejecutar este cuerpo con datos obsoletos.
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