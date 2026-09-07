package io.github.lauto5.rateLimit.infrastructure;

/**
 * Low-level port for a Redis key-value transaction bounded to a single key.
 *
 * <p>El {@link RedisTransactionPort#executeTransaction} ejecuta en una unica conexion y de
 * forma atomica la siguiente secuencia:
 *
 * <pre>
 * WATCH key
 * GET key
 * (el {@link TransactionBody} calcula el nuevo estado y TTL)
 * MULTI; SET key value PX ttl; EXEC
 * </pre>
 *
 * Si el {@code EXEC} fue abortado (otro proceso modifico la key observada), el metodo devuelve
 * {@code null} y el llamador debe reintentar con el estado mas reciente.
 *
 * <p>Este port extiende {@link AutoCloseable} para liberar los recursos subyacentes.
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