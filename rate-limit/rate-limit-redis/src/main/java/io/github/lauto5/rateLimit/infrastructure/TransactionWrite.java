package io.github.lauto5.rateLimit.infrastructure;

/**
 * Outcome of a {@link TransactionBody}: the raw bytes to persist, their time-to-live and the
 * result (if any) the caller expects back.
 *
 * @param <T> the type of the result carried back to the caller
 */
public final class TransactionWrite<T> {

	private final byte[] newValue;
	private final long ttlMillis;
	private final T result;

	public TransactionWrite(byte[] newValue, long ttlMillis, T result) {
		this.newValue = newValue;
		this.ttlMillis = ttlMillis;
		this.result = result;
	}

	/**
	 * @return the bytes to persist
	 */
	public byte[] getNewValue() {
		return newValue;
	}

	/**
	 * @return the time-to-live in milliseconds for the written value
	 */
	public long getTtlMillis() {
		return ttlMillis;
	}

	/**
	 * @return the value produced for the caller, or {@code null} if the body produced none
	 */
	public T getResult() {
		return result;
	}

}