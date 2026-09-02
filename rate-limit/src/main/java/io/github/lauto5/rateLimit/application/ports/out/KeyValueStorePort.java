package io.github.lauto5.rateLimit.application.ports.out;

public interface KeyValueStorePort extends AutoCloseable {

	byte[] get(String identifier);

	/**
	 * Reemplaza el valor de "identifier" por "newValue" solo si el valor
	 * actual coincide exactamente con "expectedValue".
	 *
	 * Si "expectedValue" es null, la operacion espera que la key NO exista
	 * todavia (escritura inicial).
	 *
	 * @return true si el reemplazo se aplico, false si hubo conflicto
	 *         (el valor actual no coincidia con lo esperado).
	 */
	boolean compareAndSwap(String identifier, byte[] expectedValue, byte[] newValue, long ttlMillis);

}
